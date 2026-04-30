{ pkgs, lib, config, inputs, ... }:

{
  languages.clojure.enable = true;
  packages = [
    pkgs.hivemind
    pkgs.caddy
  ];
}
