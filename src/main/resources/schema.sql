-- Create table for storing Kafka cluster configurations
CREATE TABLE IF NOT EXISTS cluster_info (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE,
    bootstrap_servers TEXT NOT NULL,
    kafka_version TEXT,
    security_protocol TEXT DEFAULT 'PLAINTEXT',
    sasl_mechanism TEXT,
    sasl_jaas_config TEXT,
    username TEXT,
    password TEXT,
    timeout INTEGER DEFAULT 15000,
    version_supported BOOLEAN DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS message_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    cluster_id INTEGER NOT NULL,
    topic_name TEXT NOT NULL,
    partition_id INTEGER,
    key_content TEXT,
    value_content TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Topic volume snapshots: store daily produced count and cumulative end-offset snapshot
CREATE TABLE IF NOT EXISTS topic_volume (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    cluster_id INTEGER NOT NULL,
    topic_name TEXT NOT NULL,
    day TEXT NOT NULL, -- YYYY-MM-DD
    produced_count INTEGER DEFAULT 0,
    cumulative_offset INTEGER DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(cluster_id, topic_name, day)
);
