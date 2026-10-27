UPDATE custom_field_definition
SET display_name = 'Next contact time',
    updated_time = CURRENT_TIMESTAMP(6)
WHERE object_type = 'LEAD'
  AND field_code = 'next_contact_time'
  AND display_name = 'Next lead time';
