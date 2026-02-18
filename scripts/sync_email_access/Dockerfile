FROM alpine:3.21

RUN apk add --no-cache bash curl jq openssl postgresql-client

WORKDIR /app

COPY sync.sh .
RUN chmod +x sync.sh

ENTRYPOINT ["bash", "sync.sh"]
