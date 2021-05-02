# dropbox-demo

An application accompanying [my talk about structuring functional applications in Scala](https://speakerdeck.com/kubukoz/connecting-the-dots-building-and-structuring-a-functional-application-in-scala).

## Project goals

Search images from some storage by the text on them.

I have tens of thousands of images to search, and the OCR (optical character recognition) process takes ~0.5 seconds per image for relatively small images, so live decoding is a no-no.
Instead, we will allow the user to index a path from the store, and later search the database populated in that process.

## Infrastructure

At the time of writing:

- OCR is performed by [Tesseract](https://github.com/tesseract-ocr/tesseract)
- Image source is Dropbox, we'll be using the [User API](https://www.dropbox.com/developers/documentation/http/documentation)
- Indexing and full text search are possible thanks to [Open Distro for Elasticsearch](https://opendistro.github.io).

Right now, this only runs on a local machine. Tesseract is provided via the [nix shell](https://nixos.org/), Open Distro runs in [docker](https://www.docker.com/). The application can be started using [bloop](https://scalacenter.github.io/bloop/).

## Tech stack

The backend is built in [Scala](https://scala-lang.org) (obviously - that was the point of the talk), using the following libraries:

- [Cats Effect 3](https://typelevel.org/cats-effect), for several things, such as monadic composition of asynchronous tasks (e.g. Elasticsearch client) and interop with other libraries from the ecosystem
- [http4s](https://http4s.org/) - for the HTTP server, as well as a custom client for Dropbox
- [ciris](https://cir.is) - for compositionally loading configuration
- [circe](https://circe.github.io/circe) - for decoding/encoding JSON
- [log4cats](https://typelevel.org/log4cats/) - for logging
- [Elasticsearch high-level Java client](https://www.elastic.co/guide/en/elasticsearch/client/java-rest/current/java-rest-high.html) - for talking to Elasticsearch.
  Normally you could use something like [elastic4s](https://github.com/sksamuel/elastic4s/), but I only needed a subset of its functionality and wanted to show how this can be wrapped in `cats.effect.IO`
- [weaver](https://disneystreaming.github.io/weaver-test/) - for testing
- [chimney](https://scalalandio.github.io/chimney/) - for transforming similar datatypes

The frontend is built with [React](https://reactjs.org/) + [TypeScript](https://www.typescriptlang.org/). You'll need [Node 14.x and npm](https://nodejs.org/en/), both are provided with the attached nix shell in the `frontend` directory.
