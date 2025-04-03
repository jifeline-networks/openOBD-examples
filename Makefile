mkfile_path := $(abspath $(lastword $(MAKEFILE_LIST)))
mkfile_dir := $(abspath $(dir $(mkfile_path)))

SHELL := /bin/bash
MAKEFLAGS += --no-print-directory

launcher_image_name := "openobd-example/launcher"
executor_image_name := "openobd-example/executor"

#=================================================TARGETS==============================================================#

build-example-function:
	@cd $(mkfile_dir)/functions
	@tar -cf example_function.tar.xz example_function
	@echo "You should rename the $(pwd)/example_function.tar.xz to use you own registered function uuid"

build-launcher-image:
	@cd "$(mkfile_dir)/launcher" && docker build -t "$(launcher_image_name)" .
	@echo "Created launcher image: $(launcher_image_name)"

build-executor-image:
	@cd "$(mkfile_dir)/executor" && docker build -t "$(executor_image_name)" .
	@echo "Created executor image: $(executor_image_name)"

build: build-launcher-image build-executor-image

init: build
	@cp --update=none env.dist env
	@echo "Now you can fill in the env file"

run:
	@set -a
	@source ./env
	@set +a
	@docker compose up

run-detached:
	@docker compose up -d