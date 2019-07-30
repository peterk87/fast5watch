CREATE TABLE nanopore_runs
(id BIGINT IDENTITY,
 name VARCHAR(200) NOT NULL,
 sample_id VARCHAR(200) NOT NULL,
 instrument VARCHAR(20) NOT NULL,
 flowcell_id VARCHAR(20) NOT NULL,
 original_path VARCHAR(1000) NOT NULL UNIQUE,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 added_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP(),
 local_archive_path VARCHAR(1000) DEFAULT NULL UNIQUE,
 remote_archive_path VARCHAR(1000) DEFAULT NULL UNIQUE,
 active BOOLEAN DEFAULT FALSE,
 complete BOOLEAN DEFAULT FALSE,
 started TIMESTAMP WITH TIME ZONE DEFAULT NULL,
 stopped TIMESTAMP WITH TIME ZONE DEFAULT NULL);


CREATE TABLE fast5_files
(id BIGINT IDENTITY,
 filename VARCHAR(100) NOT NULL,
 original_path VARCHAR(1000) NOT NULL UNIQUE,
 local_archive_path VARCHAR(1000) DEFAULT NULL UNIQUE,
 remote_archive_path VARCHAR(1000) DEFAULT NULL UNIQUE,
 size BIGINT NOT NULL,
 sha256 VARCHAR(64) NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 modified_at TIMESTAMP WITH TIME ZONE NOT NULL,
 nanopore_run_id BIGINT,
 FOREIGN KEY (nanopore_run_id) REFERENCES nanopore_runs(id) ON DELETE CASCADE);