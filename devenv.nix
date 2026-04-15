{ pkgs, lib, config, inputs, ... }:

{
  languages.clojure.enable = true;
  packages = [
    pkgs.babashka
    pkgs.hivemind
    pkgs.caddy
  ];
}
