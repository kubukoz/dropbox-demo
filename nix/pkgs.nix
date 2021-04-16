let
  version = "29b0d4d0b600f8f5dd0b86e3362a33d4181938f9";
in
import (
  fetchTarball {
    name = "nixpkgs-unstable-${version}";
    url = "https://github.com/NixOS/nixpkgs/archive/${version}.tar.gz";
    sha256 = "10cafssjk6wp7lr82pvqh8z7qiqwxpnh8cswnk1fbbw2pacrqxr1";
  }
)
