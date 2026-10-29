FROM alpine:3.23
RUN apk add --no-cache openssh-client
COPY start.sh /start.sh
ENTRYPOINT ["sh", "/start.sh"]
