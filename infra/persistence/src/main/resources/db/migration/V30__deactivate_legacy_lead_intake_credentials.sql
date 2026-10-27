UPDATE crm_lead_intake_client
SET is_active    = 0,
    updated_time = CURRENT_TIMESTAMP(6)
WHERE is_active = 1;
