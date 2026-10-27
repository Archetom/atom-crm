ALTER TABLE dashboard_preference
    ADD COLUMN hidden_modules VARCHAR(255) NOT NULL DEFAULT '[]' AFTER data_scope;
