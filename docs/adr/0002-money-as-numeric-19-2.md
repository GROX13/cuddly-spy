# Store money as NUMERIC(19,2)

`balance` and `amount` columns are `NUMERIC(19,2)`: exact decimals with two places, never floating point. The frozen `TimeDeposit` keeps `balance` as a `Double`, so `updateBalance` can leave binary noise such as `10.850000000000001`. Saving to the database rounds that noise away (`10.85`), while the in-memory calculator stays bit-identical to the legacy code and its characterization tests. Changing the column type later means a data migration, so this is recorded here.
