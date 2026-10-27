ALTER TABLE crm_contact
    ADD COLUMN job_title VARCHAR(200) NULL AFTER telephone,
    ADD COLUMN next_contact_time DATETIME(6) NULL AFTER description,
    ADD INDEX idx_crm_contact_tenant_next_contact (tenant_id, status, next_contact_time, id);

INSERT INTO custom_field_definition
    (tenant_id, object_type, field_code, display_name, description, field_type,
     is_builtin, is_required, is_unique, is_searchable, status, display_order,
     section_name, options_json, version)
SELECT tenant.tenant_id,
       'CONTACT',
       definition.field_code,
       definition.display_name,
       NULL,
       definition.field_type,
       1,
       0,
       0,
       1,
       'ACTIVE',
       definition.display_order,
       'Contact',
       JSON_ARRAY(),
       0
FROM organization_tenant tenant
JOIN (
    SELECT 'job_title' AS field_code, 'Job title' AS display_name, 'TEXT' AS field_type,
           35 AS display_order
    UNION ALL
    SELECT 'next_contact_time', 'Next contact time', 'DATETIME', 62
) definition ON 1 = 1;
