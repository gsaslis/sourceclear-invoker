PROJECT_NAME := sourceclear-invoker
ROOT_DIR := $(shell dirname $(realpath $(firstword $(MAKEFILE_LIST))))

PRODUCT_NAME :=
PRODUCT_VERSION :=
SCM_URL :=
SCM_REF :=

COMMAND := mvn -f /scanning/srcclr-invoker/pom.xml -Pjenkins clean test -DargLine='-Dsourceclear="--product=$(PRODUCT_NAME) --product-version=$(PRODUCT_VERSION) scm --url=$(SCM_URL) --ref=$(SCM_REF) "'

build:
	docker build -t $(PROJECT_NAME):latest .

test:
	docker run --rm -v $(ROOT_DIR)/src/test/resources/agent.yml:/etc/srcclr/agent.yml $(PROJECT_NAME):latest $(COMMAND)