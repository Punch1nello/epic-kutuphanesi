# Technical notes

- Epic auth adapter follows the current Legendary-style authorization-code flow.
- Library retrieval uses the current legacy library-service + catalog-service endpoints used by Legendary.
- Pagination follows `responseMetadata.nextCursor`.
- Non-Windows-only entries and Unreal/tool namespaces are filtered.
- Catalog enrichment prefers `DieselGameBoxTall` / `DieselGameBox` images.
- Refresh tokens are encrypted with AES/GCM using an Android Keystore key and only the encrypted value is persisted in SharedPreferences.
- The UI never receives or logs the Epic password.
- If Epic changes the auth flow, update `EpicAuth.kt` only.
