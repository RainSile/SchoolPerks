// Build inside the pinned MinIO source checkout, using its existing go.mod/go.sum.
package main

import (
	"context"
	"encoding/json"
	"fmt"
	"net/url"
	"os"
	"strconv"
	"strings"
	"time"

	"github.com/minio/madmin-go/v3"
	"github.com/minio/minio-go/v7"
	"github.com/minio/minio-go/v7/pkg/credentials"
	"github.com/minio/minio-go/v7/pkg/lifecycle"
)

func main() {
	if err := configure(); err != nil {
		fmt.Fprintln(os.Stderr, "Private storage bootstrap failed; check private settings and server availability.")
		os.Exit(1)
	}
	fmt.Println("Private bucket, quota, pending lifecycle and restricted application principal configured.")
}

func configure() error {
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()
	endpoint := os.Getenv("MINIO_BOOTSTRAP_ENDPOINT")
	if endpoint == "" {
		endpoint = "http://127.0.0.1:9000"
	}
	u, err := url.Parse(endpoint)
	if err != nil || u.Host == "" || (u.Scheme != "http" && u.Scheme != "https") || u.User != nil || u.RawQuery != "" || u.Fragment != "" || (u.Path != "" && u.Path != "/") {
		return fmt.Errorf("invalid bootstrap endpoint")
	}
	region := os.Getenv("MINIO_REGION")
	if region == "" {
		region = "us-east-1"
	}
	root, secret := os.Getenv("MINIO_ROOT_USER"), os.Getenv("MINIO_ROOT_PASSWORD")
	bucket, app, appSecret := os.Getenv("MINIO_BUCKET"), os.Getenv("MINIO_APP_ACCESS_KEY"), os.Getenv("MINIO_APP_SECRET_KEY")
	prefix := os.Getenv("FILE_OBJECT_PREFIX")
	if root == "" || secret == "" || bucket == "" || app == "" || appSecret == "" || prefix == "" || strings.ContainsAny(prefix, "*?") || strings.Contains(prefix, "..") || strings.HasPrefix(prefix, "/") || strings.HasSuffix(prefix, "/") {
		return fmt.Errorf("invalid or missing private settings")
	}
	size, err := strconv.ParseUint(os.Getenv("MINIO_BUCKET_QUOTA_BYTES"), 10, 64)
	if err != nil || size == 0 {
		return fmt.Errorf("invalid quota")
	}
	client, err := minio.New(u.Host, &minio.Options{Creds: credentials.NewStaticV4(root, secret, ""), Region: region, Secure: u.Scheme == "https"})
	if err != nil {
		return err
	}
	admin, err := madmin.New(u.Host, root, secret, u.Scheme == "https")
	if err != nil {
		return err
	}
	exists, err := client.BucketExists(ctx, bucket)
	if err != nil {
		return err
	}
	if !exists {
		if err = client.MakeBucket(ctx, bucket, minio.MakeBucketOptions{Region: region}); err != nil {
			return err
		}
	}
	if err = client.SetBucketPolicy(ctx, bucket, ""); err != nil {
		return err
	}
	if err = admin.SetBucketQuota(ctx, bucket, &madmin.BucketQuota{Quota: size, Size: size, Type: madmin.HardQuota}); err != nil {
		return err
	}
	lc := lifecycle.NewConfiguration()
	lc.Rules = []lifecycle.Rule{{ID: "expire-unconfirmed-uploads", Status: "Enabled", RuleFilter: lifecycle.Filter{Prefix: prefix + "/pending/"}, Expiration: lifecycle.Expiration{Days: 1}, AbortIncompleteMultipartUpload: lifecycle.AbortIncompleteMultipartUpload{DaysAfterInitiation: 1}}}
	if err = client.SetBucketLifecycle(ctx, bucket, lc); err != nil {
		return err
	}
	policy := map[string]any{"Version": "2012-10-17", "Statement": []map[string]any{
		{"Effect": "Allow", "Action": []string{"s3:GetBucketLocation", "s3:ListBucket"}, "Resource": []string{"arn:aws:s3:::" + bucket}},
		{"Effect": "Allow", "Action": []string{"s3:GetObject", "s3:PutObject", "s3:DeleteObject", "s3:AbortMultipartUpload", "s3:ListMultipartUploadParts"}, "Resource": []string{"arn:aws:s3:::" + bucket + "/" + prefix + "/*"}},
	}}
	data, err := json.Marshal(policy)
	if err != nil {
		return err
	}
	if err = admin.AddUser(ctx, app, appSecret); err != nil {
		return err
	}
	if err = admin.AddCannedPolicy(ctx, "campus-files", data); err != nil {
		return err
	}
	return admin.SetPolicy(ctx, "campus-files", app, false)
}
