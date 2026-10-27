ALTER TABLE crm_return_visit
    ADD COLUMN visit_form VARCHAR(20) NULL AFTER visit_time,
    ADD COLUMN satisfaction VARCHAR(24) NULL AFTER visit_form,
    ADD CONSTRAINT chk_crm_return_visit_form
        CHECK (visit_form IS NULL OR visit_form IN ('IN_PERSON', 'PHONE', 'SMS', 'EMAIL', 'WECHAT')),
    ADD CONSTRAINT chk_crm_return_visit_satisfaction
        CHECK (satisfaction IS NULL OR satisfaction IN (
            'VERY_SATISFIED', 'SATISFIED', 'NEUTRAL', 'DISSATISFIED', 'VERY_DISSATISFIED'
        ));

INSERT INTO custom_field_definition
    (tenant_id, object_type, field_code, display_name, description, field_type,
     is_builtin, is_required, is_unique, is_searchable, status, display_order,
     section_name, options_json, version)
SELECT tenant.tenant_id, definition.object_type, definition.field_code, definition.display_name,
       NULL, definition.field_type, 1, 0, 0, 1, 'ACTIVE', definition.display_order,
       'Return visit', definition.options_json, 0
FROM organization_tenant tenant
JOIN (
    SELECT 'RETURN_VISIT' object_type, 'visit_form' field_code, 'Visit form' display_name,
           'SELECT' field_type, 60 display_order,
           JSON_ARRAY(
               JSON_OBJECT('value', 'IN_PERSON', 'label', 'In-person', 'order', 0),
               JSON_OBJECT('value', 'PHONE', 'label', 'Phone', 'order', 1),
               JSON_OBJECT('value', 'SMS', 'label', 'SMS', 'order', 2),
               JSON_OBJECT('value', 'EMAIL', 'label', 'Email', 'order', 3),
               JSON_OBJECT('value', 'WECHAT', 'label', 'WeChat', 'order', 4)
           ) options_json
    UNION ALL
    SELECT 'RETURN_VISIT', 'satisfaction', 'Customer satisfaction', 'SELECT', 70,
           JSON_ARRAY(
               JSON_OBJECT('value', 'VERY_SATISFIED', 'label', 'Very satisfied', 'order', 0),
               JSON_OBJECT('value', 'SATISFIED', 'label', 'Satisfied', 'order', 1),
               JSON_OBJECT('value', 'NEUTRAL', 'label', 'Neutral', 'order', 2),
               JSON_OBJECT('value', 'DISSATISFIED', 'label', 'Dissatisfied', 'order', 3),
               JSON_OBJECT('value', 'VERY_DISSATISFIED', 'label', 'Very dissatisfied', 'order', 4)
           )
) definition ON 1 = 1;
