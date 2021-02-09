FROM fedora:31

ENV APP_ROOT=/scanning
ENV PATH=${APP_ROOT}/bin:${PATH}
ENV HOME=${APP_ROOT}

RUN printf "[sourceclear]\nbaseurl = https://download.sourceclear.com/redhat/noarch/\nenabled = 1\ngpgcheck = 1\ngpgkey = https://download.sourceclear.com/redhat/SRCCLR-GPG-KEY\nname = SourceClear YUM Repository" > /etc/yum.repos.d/sourceclear.repo && \
    echo "gpgkey=http://hdn.corp.redhat.com/rhel7-csb-stage/RPM-GPG-KEY-helpdesk" >> /etc/yum.repos.d/rhel7-csb-stage.repo

RUN dnf install -v -y \
        srcclr \
        git \
        java-1.8.0-openjdk \
        maven


RUN mkdir -p ${APP_ROOT}/srcclr-invoker && \
    mkdir -p ${APP_ROOT}/bin && \
    mkdir -p /etc/srcclr && \
    curl -sSkL https://github.com/project-ncl/sourceclear-invoker/tarball/srcclr-1.5 | tar -xz --strip-components=1 -C ${APP_ROOT}/srcclr-invoker

RUN chmod -R og+r /etc/yum.repos.d && \
    chgrp -R 0 ${APP_ROOT} && \
    chmod -R u+x ${APP_ROOT}/bin && \
    chmod -R g=u ${APP_ROOT} /etc/passwd /etc/srcclr

### Containers should NOT run as root as a good practice
USER 10001
WORKDIR ${APP_ROOT}
