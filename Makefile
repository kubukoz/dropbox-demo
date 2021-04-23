dcp: docker-compose.dhall docker-compose.yml
	cat docker-compose.dhall | dhall-to-yaml > docker-compose.yml
fmt: build.sbt src
	scalafmt build.sbt src

# Generate this with the scala-sculpt compiler plugin
# https://github.com/lightbend/scala-sculpt/tree/b6d69f0aaeaa76e1c57d6687f225b250d4bf00c3
#
# Add these to the root sbt project
# and run `sbt compile`
#
# scalacOptions ++= Seq(
#   "-Xplugin:/path/to/scala-sculpt_2.13-0.1.4-SNAPSHOT.jar",
#   "-Xplugin-require:sculpt",
#   "-P:sculpt:mode=class",
#   "-P:sculpt:out=sculpt/sculpt.json",
# ),
#
sculpt: sculpt/graph.sc sculpt/sculpt.json
	cd sculpt; (amm graph.sc > sculpt.dot) && (dot -Tsvg sculpt.dot > sculpt.svg) && open sculpt.svg
