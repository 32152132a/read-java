ALTER TABLE content_entries ADD COLUMN config_json LONGTEXT NULL AFTER version;

UPDATE content_entries
SET config_json = (
  SELECT content_revisions.config_json
  FROM content_revisions
  WHERE content_revisions.entry_id = content_entries.id
    AND content_revisions.version = content_entries.version
)
WHERE version > 0;

DROP TABLE content_revisions;
ALTER TABLE content_entries DROP COLUMN published_version;

-- 旧任务可能已经生成草稿但尚未关联词库，重新入队后会复用现有配置并完成关联。
UPDATE content_job_items
SET status = 'PENDING', error_message = NULL
WHERE status = 'SUCCEEDED' AND entry_id IS NOT NULL;

UPDATE content_jobs
SET status = 'PENDING'
WHERE id IN (SELECT DISTINCT job_id FROM content_job_items WHERE status = 'PENDING');
