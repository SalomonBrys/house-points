-- Migration 0 -> 1: add team images (schema0.sql -> schema1.sql).
ALTER TABLE teampoints_teams ADD COLUMN image_filename VARCHAR(255) NULL;
