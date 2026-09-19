# API conventions

REST controllers return `ApiResponse<T>`. Successful responses set `data`; paginated
responses also set `pagination` with zero-based `page`, `size`, `totalElements`, and
`totalPages`. Errors set `error.code` and `error.message`. Absent fields are omitted.

Throw the matching `ApiException` subtype for expected business failures. Invalid
request bodies return `VALIDATION_ERROR`; unexpected failures return a generic
`INTERNAL_ERROR` message without exposing implementation details.

Keep persistence entities inside the data layer. Define request and response DTOs
for each endpoint and convert them explicitly in the application or web layer.
Never expose an entity directly. Mapping must copy only fields intended for the
API; do not bind client input directly into an entity. API changes that alter
existing fields or semantics require a versioned endpoint.
