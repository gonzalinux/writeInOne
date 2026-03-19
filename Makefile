ENV_FILE := .env

# Load .env if it exists
ifneq (,$(wildcard $(ENV_FILE)))
  include $(ENV_FILE)
  export $(shell sed 's/=.*//' $(ENV_FILE))
endif

.PHONY: run watch db test docker-test prod down

## run: Start the app locally with bootRun (pair with `make watch` for auto-reload)
run:
	./gradlew bootRun

## watch: Continuously recompile Kotlin and copy resources on file changes (DevTools will restart the app)
watch:
	./gradlew -t compileKotlin processResources

## db: Start only the database via Docker Compose
db:
	mkdir -p postgres_data && docker compose up db

## test: Run tests locally
test:
	./gradlew test

## docker-test: Run tests inside Docker Compose (uses existing compose, overrides command)
docker-test:
	docker compose run --rm app ./gradlew test

## prod: Load .env and start app + db via Docker Compose
prod:
	mkdir -p postgres_data && docker compose --env-file $(ENV_FILE) up --build -d

## down: Stop all running containers
down:
	docker compose down
