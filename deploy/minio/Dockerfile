FROM public.ecr.aws/docker/library/alpine@sha256:2e0b81d60597f449baa56dc25d355367869d2dca4cd57e866f1e2b52af3eb55f
LABEL org.opencontainers.image.source="https://github.com/minio/minio" \
      org.opencontainers.image.revision="9e49d5e7a648f00e26f2246f4dc28e6b07f8c84a"
COPY --chmod=0555 build/minio /usr/local/bin/minio
COPY --chmod=0444 build/LICENSE /usr/share/licenses/minio/LICENSE
USER 10001:10001
ENTRYPOINT ["/usr/local/bin/minio"]
