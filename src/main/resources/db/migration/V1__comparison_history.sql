-- Scores and filenames only: submitted source code is never persisted.
CREATE TABLE comparison_runs (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    created_at TIMESTAMP(6) NOT NULL,
    run_type VARCHAR(8) NOT NULL,
    submission_count INT NOT NULL,
    pair_count INT NOT NULL,
    top_similarity DECIMAL(5,2) NULL
);

CREATE INDEX idx_runs_created ON comparison_runs (created_at, id);

CREATE TABLE comparison_pairs (
    run_id VARCHAR(36) NOT NULL,
    pair_position INT NOT NULL,
    first_name TEXT NOT NULL,
    second_name TEXT NOT NULL,
    similarity DECIMAL(5,2) NOT NULL,
    verdict VARCHAR(6) NOT NULL,
    PRIMARY KEY (run_id, pair_position),
    CONSTRAINT fk_pairs_run FOREIGN KEY (run_id) REFERENCES comparison_runs (id)
);
