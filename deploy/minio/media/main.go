// Build in the existing pinned MinIO source checkout, without changing go.mod/go.sum.
package main

import (
	"context"
	"encoding/json"
	"fmt"
	"github.com/minio/madmin-go/v3"
	"github.com/minio/minio-go/v7"
	"github.com/minio/minio-go/v7/pkg/credentials"
	"github.com/minio/minio-go/v7/pkg/lifecycle"
	"image/png"
	"net/url"
	"os"
	"path/filepath"
	"regexp"
	"strings"
	"time"
)

func setting(key, fallback string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return fallback
}
func prefix(value string) bool {
	return value != "" && !strings.HasPrefix(value, "/") && !strings.HasSuffix(value, "/") && !strings.Contains(value, "..") && !strings.ContainsAny(value, "*?\r\n")
}
func backup(name string, data []byte) error {
	if _, err := os.Stat(name); err == nil {
		return nil
	}
	return os.WriteFile(name, data, 0600)
}
func configure() error {
	ctx, cancel := context.WithTimeout(context.Background(), 40*time.Second)
	defer cancel()
	endpoint, err := url.Parse(setting("MINIO_BOOTSTRAP_ENDPOINT", "http://127.0.0.1:9000"))
	if err != nil || endpoint.Host == "" || endpoint.User != nil || endpoint.RawQuery != "" || endpoint.Fragment != "" || endpoint.Path != "" || (endpoint.Scheme != "http" && endpoint.Scheme != "https") {
		return fmt.Errorf("invalid endpoint")
	}
	bucket, root, secret := os.Getenv("MINIO_BUCKET"), os.Getenv("MINIO_ROOT_USER"), os.Getenv("MINIO_ROOT_PASSWORD")
	if bucket == "" || root == "" || secret == "" {
		return fmt.Errorf("missing settings")
	}
	client, err := minio.New(endpoint.Host, &minio.Options{Creds: credentials.NewStaticV4(root, secret, ""), Secure: endpoint.Scheme == "https", Region: setting("MINIO_REGION", "us-east-1")})
	if err != nil {
		return err
	}
	admin, err := madmin.New(endpoint.Host, root, secret, endpoint.Scheme == "https")
	if err != nil {
		return err
	}
	versioning, err := client.GetBucketVersioning(ctx, bucket)
	if err != nil {
		return err
	}
	if versioning.Status != "" {
		return fmt.Errorf("versioned storage requires a separately reviewed physical-deletion policy")
	}
	prefixes := []string{setting("FILE_STUDENT_CARD_PREFIX", "private/student-cards"), setting("FILE_AVATAR_PREFIX", "private/avatars/custom"), setting("FILE_COMMUNITY_IMAGE_PREFIX", "private/community-images")}
	defaults := setting("AVATAR_DEFAULT_PREFIX", "private/avatars/default")
	for _, value := range append(prefixes, defaults) {
		if !prefix(value) {
			return fmt.Errorf("invalid prefix")
		}
	}
	oldPolicy, err := admin.InfoCannedPolicy(ctx, "campus-files")
	if err != nil {
		return err
	}
	if err = backup("media-policy-before.json", oldPolicy); err != nil {
		return err
	}
	var policy map[string]any
	if err = json.Unmarshal(oldPolicy, &policy); err != nil {
		return err
	}
	statements, ok := policy["Statement"].([]any)
	if !ok {
		return fmt.Errorf("unknown policy format")
	}
	kept := []any{}
	for _, statement := range statements {
		entry, ok := statement.(map[string]any)
		if !ok {
			return fmt.Errorf("unknown statement")
		}
		if entry["Sid"] != "CampusBusinessImagesV15" && entry["Sid"] != "CampusDefaultAvatarsV15" {
			kept = append(kept, entry)
		}
	}
	resources := []string{}
	for _, value := range prefixes {
		resources = append(resources, "arn:aws:s3:::"+bucket+"/"+value+"/*")
	}
	kept = append(kept, map[string]any{"Sid": "CampusBusinessImagesV15", "Effect": "Allow", "Action": []string{"s3:GetObject", "s3:PutObject", "s3:DeleteObject", "s3:AbortMultipartUpload", "s3:ListMultipartUploadParts"}, "Resource": resources})
	kept = append(kept, map[string]any{"Sid": "CampusDefaultAvatarsV15", "Effect": "Allow", "Action": []string{"s3:GetObject"}, "Resource": []string{"arn:aws:s3:::" + bucket + "/" + defaults + "/*"}})
	policy["Statement"] = kept
	updated, err := json.Marshal(policy)
	if err != nil {
		return err
	}
	oldLifecycle, err := client.GetBucketLifecycle(ctx, bucket)
	if err != nil {
		return err
	}
	lifeBackup, err := json.Marshal(oldLifecycle)
	if err != nil {
		return err
	}
	if err = backup("media-lifecycle-before.json", lifeBackup); err != nil {
		return err
	}
	for i, value := range prefixes {
		id := fmt.Sprintf("campus-media-pending-%d", i)
		found := false
		for _, rule := range oldLifecycle.Rules {
			if rule.ID == id {
				found = true
			}
		}
		if !found {
			oldLifecycle.Rules = append(oldLifecycle.Rules, lifecycle.Rule{ID: id, Status: "Enabled", RuleFilter: lifecycle.Filter{Prefix: value + "/pending/"}, Expiration: lifecycle.Expiration{Days: 1}, AbortIncompleteMultipartUpload: lifecycle.AbortIncompleteMultipartUpload{DaysAfterInitiation: 1}})
		}
	}
	if err = admin.AddCannedPolicy(ctx, "campus-files", updated); err != nil {
		return err
	}
	if err = client.SetBucketLifecycle(ctx, bucket, oldLifecycle); err != nil {
		return err
	}
	dir := os.Getenv("DEFAULT_AVATAR_DIRECTORY")
	if dir == "" {
		return nil
	}
	entries, err := os.ReadDir(dir)
	if err != nil {
		return err
	}
	count := 0
	pattern := regexp.MustCompile("^[a-z0-9-]+\\.png$")
	for _, entry := range entries {
		if entry.IsDir() || !pattern.MatchString(entry.Name()) {
			continue
		}
		filename := filepath.Join(dir, entry.Name())
		file, err := os.Open(filename)
		if err != nil {
			return err
		}
		cfg, decodeErr := png.DecodeConfig(file)
		file.Close()
		if decodeErr != nil || cfg.Width != 256 || cfg.Height != 256 {
			return fmt.Errorf("invalid default avatar")
		}
		if _, err = client.FPutObject(ctx, bucket, defaults+"/"+entry.Name(), filename, minio.PutObjectOptions{ContentType: "image/png"}); err != nil {
			return err
		}
		count++
	}
	if count != 4 {
		return fmt.Errorf("expected four default avatars")
	}
	fmt.Printf("Stored %d default avatars; media prefixes isolated; existing policy rules retained; versioning disabled.\n", count)
	return nil
}
func main() {
	if err := configure(); err != nil {
		fmt.Fprintln(os.Stderr, "Business-image storage configuration failed; inspect private configuration without exposing credentials.")
		os.Exit(1)
	}
}
