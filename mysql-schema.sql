CREATE TABLE IF NOT EXISTS event_journal(
    ordering SERIAL,
    deleted BOOLEAN DEFAULT false NOT NULL,
    persistence_id VARCHAR(255) NOT NULL,
    sequence_number BIGINT NOT NULL,
    writer TEXT NOT NULL,
    write_timestamp BIGINT NOT NULL,
    adapter_manifest TEXT NOT NULL,
    event_payload BLOB NOT NULL,
    event_ser_id INTEGER NOT NULL,
    event_ser_manifest TEXT NOT NULL,
    meta_payload BLOB,
    meta_ser_id INTEGER,meta_ser_manifest TEXT,
    PRIMARY KEY(persistence_id,sequence_number)
);

CREATE UNIQUE INDEX event_journal_ordering_idx ON event_journal(ordering);

CREATE TABLE IF NOT EXISTS event_tag (
    event_id BIGINT UNSIGNED NOT NULL,
    tag VARCHAR(255) NOT NULL,
    PRIMARY KEY(event_id, tag),
    FOREIGN KEY (event_id)
        REFERENCES event_journal(ordering)
        ON DELETE CASCADE
    );

CREATE TABLE IF NOT EXISTS snapshot (
    persistence_id VARCHAR(255) NOT NULL,
    sequence_number BIGINT NOT NULL,
    created BIGINT NOT NULL,
    snapshot_ser_id INTEGER NOT NULL,
    snapshot_ser_manifest TEXT NOT NULL,
    snapshot_payload BLOB NOT NULL,
    meta_ser_id INTEGER,
    meta_ser_manifest TEXT,
    meta_payload BLOB,
  PRIMARY KEY (persistence_id, sequence_number));

CREATE TABLE `db`.`writer_last_sequence_snapshot` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `persistence_id` VARCHAR(45) NOT NULL,
  `sequence` INT NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `id_UNIQUE` (`id` ASC),
  INDEX `persistence_id` (`persistence_id` ASC));

CREATE TABLE `db`.`wallet_hour_projection` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `wallet_id` VARCHAR(45) NOT NULL,
  `balance` FLOAT NOT NULL,
  `date_time_projection` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `id_UNIQUE` (`id` ASC),
  INDEX `wallet_id_idx` (`wallet_id` ASC),
  INDEX `date_time_idx` (`date_time_projection` ASC));

CREATE TABLE `db`.`reader_last_sequence_snapshot` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `persistence_id` VARCHAR(45) NOT NULL,
  `sequence` INT NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `id_UNIQUE` (`id` ASC),
  INDEX `persistence_id` (`persistence_id` ASC));

CREATE TABLE `db`.`consumer_last_offset_snapshot` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `persistence_id` VARCHAR(45) NOT NULL,
  `offset` INT NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `id_UNIQUE` (`id` ASC),
  INDEX `persistence_id` (`persistence_id` ASC));