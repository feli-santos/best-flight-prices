# Localization

Languages: en (default), pt (pt-BR), es. Compose resources: composeResources/values, values-pt, values-es. Android: locales_config.xml; iOS: CFBundleLocalizations. Server sends ReasonCodes; client maps via exhaustive `when`. Prices/dates use a platform formatter (expect/actual) with identical-output tests. `make strings-check` fails on missing keys.
