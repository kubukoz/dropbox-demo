let Compose =
      https://raw.githubusercontent.com/sbdchd/dhall-docker-compose/master/compose/v3/package.dhall sha256:bff77b825ce0eb3bad0c0bb5e365b10a09f9315da32206e5b8c71682ff985f95

let elastic =
      Compose.Service::{
      , image = Some "amazon/opendistro-for-elasticsearch:1.13.1"
      , ports = Some
        [ Compose.StringOrNumber.String "9200:9200"
        , Compose.StringOrNumber.String "9600:9600"
        ]
      , environment = Some
          ( Compose.ListOrDict.Dict
              [ { mapKey = "opendistro_security.ssl.http.enabled"
                , mapValue = "false"
                }
              , { mapKey = "discovery.type", mapValue = "single-node" }
              ]
          )
      }

in  Compose.Config::{ services = Some (toMap { elastic }) }
