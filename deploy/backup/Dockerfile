# The backup service of an installation (TASK-131, DEC-161): the client tools of PostgreSQL 18 with restic, nothing else.
# It never serves a port. Base images are pinned by tag and digest so that a rebuild uses the same tool versions.
FROM restic/restic:0.18.1@sha256:39d9072fb5651c80d75c7a811612eb60b4c06b32ffe87c2e9f3c7222e1797e76 AS restic

FROM postgres:18.6@sha256:74935e72241653ca55e0414067e6d8763aceb8a810eb51b452253ec3dcfc4336
COPY --from=restic /usr/bin/restic /usr/local/bin/restic
COPY backup.sh restore.sh lib.sh /opt/backup/
RUN chmod 0555 /opt/backup/*.sh && restic version
# The service runs as the postgres account of the image, which owns nothing of the application: it reads the write ahead
# log archive and the file store and writes only to the backup target and its own state directory.
USER postgres
ENTRYPOINT ["/opt/backup/backup.sh"]
