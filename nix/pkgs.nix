let
  version = "17f061226736b76bcb4b807cf32f22fd61d81464";
in
import (
  fetchTarball {
    name = "nixpkgs-unstable-${version}";
    url = "https://github.com/NixOS/nixpkgs/archive/${version}.tar.gz";
    sha256 = "1047v990my1r2dglk4n84i0wihcf5mndxnbyz8i8asv41ab3ac9i";
  }
)
