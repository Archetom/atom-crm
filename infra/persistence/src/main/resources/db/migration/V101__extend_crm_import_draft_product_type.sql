ALTER TABLE crm_import_draft
    DROP CHECK chk_crm_import_draft_object_type,
    ADD CONSTRAINT chk_crm_import_draft_object_type CHECK (
        object_type IN ('LEAD', 'CONTACT', 'PRODUCT')
    );
