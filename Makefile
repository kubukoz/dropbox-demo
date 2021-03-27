dcp: docker-compose.dhall docker-compose.yml
	cat docker-compose.dhall | dhall-to-yaml > docker-compose.yml
