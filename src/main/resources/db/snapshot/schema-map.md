# Schema map — `app`

> **GENERATED — do not edit.** Final database after every migration, regenerated with `scripts/db/snapshot.sh --fresh` in the same PR as each new migration (the CI bot commits it if forgotten). Latest migration: V254. Read this instead of replaying migrations. Full DDL: [`schema.sql`](schema.sql); seeded rows: [`data.sql`](data.sql).

116 tables.

`affiliation_request_logs` · `affiliation_requests` · `allies` · `ally_agreements` · `ally_ally_types` · `ally_opening_hours` · `ally_patient_links` · `ally_product_review_log` · `ally_products` · `ally_professions` · `ally_types` · `ally_users` · `attached_files` · `banks` · `beneficiaries` · `benefit_usages` · `campaign_promoters` · `campaign_transaction_exceptions` · `campaign_transaction_links` · `campaigns` · `cities` · `collection_commission_tier_promoter_types` · `collection_commission_tiers` · `commission_bonus_rule_promoter_types` · `commission_bonus_rules` · `commission_retroactive_topup_cuts` · `commission_retroactive_topups` · `commission_tier_promoter_types` · `commission_tiers` · `commissions` · `company_bank_account_payment_methods` · `company_bank_accounts` · `competitive_commission_award_settlements` · `competitive_commission_awards` · `competitive_commission_manual_decisions` · `competitive_commission_rule_positions` · `competitive_commission_rule_promoter_types` · `competitive_commission_rule_ranks` · `competitive_commission_rules` · `competitive_commission_tie_candidates` · `competitive_commission_ties` · `contact_messages` · `corporate_contracts` · `countries` · `currencies` · `data_change_audit_log` · `document_types` · `entity_config` · `exchange_rates` · `file_download_links` · `file_type_policies` · `genders` · `hierarchy_override_tiers` · `holidays` · `licensing_bodies` · `login_audit_log` · `marital_statuses` · `media_items` · `medical_record_access_log` · `medical_records` · `member_documents` · `member_promoter_assignments` · `members` · `membership_charges` · `membership_promotions` · `memberships` · `notifications` · `occupations` · `onboarding_policies` · `organizations` · `payer_bank_account_payment_methods` · `payer_bank_accounts` · `payment_categories` · `payment_credit_claims` · `payment_lines` · `payment_methods` · `payments` · `permission_domains` · `permissions` · `person_credit_entries` · `person_professional_licenses` · `persons` · `plans` · `product_categories` · `products` · `professions` · `promoter_bonus_awards` · `promoter_hierarchy_overrides` · `promoter_member_contacts` · `promoter_payout_account_payment_methods` · `promoter_payout_accounts` · `promoter_ranks` · `promoter_references` · `promoter_supervisor_assignments` · `promoter_types` · `promoters` · `promotion_plans` · `promotions` · `referrals` · `report_audit_log` · `report_share` · `role_permissions` · `roles` · `scheduled_job_runs` · `scheduled_jobs` · `security_policies` · `states` · `subsidies` · `subsidy_audit_log` · `subsidy_beneficiaries` · `system_configs` · `terms_acceptances` · `terms_versions` · `user_password_history` · `user_roles` · `users`

# Relationships (child → parent)

- `affiliation_request_logs`.actor_user_id → `users`.users_id
- `affiliation_request_logs`.affiliation_request_id → `affiliation_requests`.affiliation_requests_id (ON DELETE CASCADE)
- `affiliation_requests`.member_id → `members`.members_id
- `affiliation_requests`.membership_id → `memberships`.memberships_id
- `affiliation_requests`.payment_bank_id → `banks`.banks_id
- `affiliation_requests`.payment_id → `payments`.payments_id
- `affiliation_requests`.payment_method_id → `payment_methods`.payment_methods_id
- `affiliation_requests`.plan_id → `plans`.plans_id
- `affiliation_requests`.promoter_id → `promoters`.promoters_id
- `affiliation_requests`.reviewed_by → `users`.users_id
- `affiliation_requests`.terms_versions_id → `terms_versions`.terms_versions_id
- `affiliation_requests`.user_id → `users`.users_id
- `allies`.captured_by_promoter_id → `promoters`.promoters_id
- `allies`.city_id → `cities`.cities_id
- `allies`.host_ally_id → `allies`.allies_id
- `allies`.person_id → `persons`.persons_id
- `ally_agreements`.ally_id → `allies`.allies_id
- `ally_agreements`.terms_version_id → `terms_versions`.terms_versions_id
- `ally_ally_types`.ally_id → `allies`.allies_id (ON DELETE CASCADE)
- `ally_ally_types`.ally_type_id → `ally_types`.ally_types_id
- `ally_opening_hours`.ally_id → `allies`.allies_id (ON DELETE CASCADE)
- `ally_patient_links`.created_at_ally_id → `allies`.allies_id
- `ally_patient_links`.created_by_user_id → `users`.users_id
- `ally_patient_links`.doctor_person_id → `persons`.persons_id
- `ally_patient_links`.member_id → `members`.members_id
- `ally_product_review_log`.actor_user_id → `users`.users_id
- `ally_product_review_log`.ally_product_id → `ally_products`.ally_products_id (ON DELETE CASCADE)
- `ally_products`.ally_id → `allies`.allies_id (ON DELETE CASCADE)
- `ally_products`.price_currency_id → `currencies`.currencies_id
- `ally_products`.product_id → `products`.products_id
- `ally_products`.reviewed_by → `users`.users_id
- `ally_professions`.ally_id → `allies`.allies_id (ON DELETE CASCADE)
- `ally_professions`.profession_id → `professions`.professions_id
- `ally_users`.ally_id → `allies`.allies_id (ON DELETE CASCADE)
- `ally_users`.user_id → `users`.users_id
- `attached_files`.published_by → `users`.users_id
- `attached_files`.uploaded_by → `users`.users_id
- `beneficiaries`.inscription_payment_id → `payments`.payments_id
- `beneficiaries`.member_id → `members`.members_id (ON DELETE CASCADE)
- `beneficiaries`.person_id → `persons`.persons_id
- `beneficiaries`.user_id → `users`.users_id
- `benefit_usages`.ally_id → `allies`.allies_id
- `benefit_usages`.ally_product_id → `ally_products`.ally_products_id
- `benefit_usages`.ally_user_id → `ally_users`.ally_users_id
- `benefit_usages`.beneficiary_id → `beneficiaries`.beneficiaries_id
- `benefit_usages`.consumption_currency_id → `currencies`.currencies_id
- `benefit_usages`.converted_currency_id → `currencies`.currencies_id
- `benefit_usages`.copay_currency_id → `currencies`.currencies_id
- `benefit_usages`.membership_id → `memberships`.memberships_id
- `benefit_usages`.product_category_id → `product_categories`.product_categories_id
- `campaign_promoters`.campaign_id → `campaigns`.campaigns_id
- `campaign_promoters`.promoter_id → `promoters`.promoters_id
- `campaign_transaction_exceptions`.campaign_id → `campaigns`.campaigns_id
- `campaign_transaction_exceptions`.membership_id → `memberships`.memberships_id
- `campaign_transaction_exceptions`.payment_id → `payments`.payments_id
- `campaign_transaction_links`.campaign_id → `campaigns`.campaigns_id
- `campaign_transaction_links`.membership_id → `memberships`.memberships_id
- `campaign_transaction_links`.payment_id → `payments`.payments_id
- `campaigns`.target_amount_currency_id → `currencies`.currencies_id
- `cities`.state_id → `states`.states_id
- `collection_commission_tier_promoter_types`.collection_commission_tier_id → `collection_commission_tiers`.collection_commission_tiers_id (ON DELETE CASCADE)
- `collection_commission_tier_promoter_types`.promoter_type_id → `promoter_types`.promoter_types_id
- `collection_commission_tiers`.campaign_id → `campaigns`.campaigns_id
- `collection_commission_tiers`.flat_amount_currency_id → `currencies`.currencies_id
- `collection_commission_tiers`.min_amount_currency_id → `currencies`.currencies_id
- `commission_bonus_rule_promoter_types`.commission_bonus_rule_id → `commission_bonus_rules`.commission_bonus_rules_id (ON DELETE CASCADE)
- `commission_bonus_rule_promoter_types`.promoter_type_id → `promoter_types`.promoter_types_id
- `commission_bonus_rules`.campaign_id → `campaigns`.campaigns_id
- `commission_bonus_rules`.reward_currency_id → `currencies`.currencies_id
- `commission_bonus_rules`.threshold_currency_id → `currencies`.currencies_id
- `commission_retroactive_topup_cuts`.currency_id → `currencies`.currencies_id
- `commission_retroactive_topup_cuts`.payout_payment_id → `payments`.payments_id
- `commission_retroactive_topup_cuts`.promoter_id → `promoters`.promoters_id
- `commission_retroactive_topups`.currency_id → `currencies`.currencies_id
- `commission_retroactive_topups`.payout_payment_id → `payments`.payments_id
- `commission_retroactive_topups`.promoter_id → `promoters`.promoters_id
- `commission_tier_promoter_types`.commission_tier_id → `commission_tiers`.commission_tiers_id (ON DELETE CASCADE)
- `commission_tier_promoter_types`.promoter_type_id → `promoter_types`.promoter_types_id
- `commission_tiers`.campaign_id → `campaigns`.campaigns_id
- `commission_tiers`.flat_amount_currency_id → `currencies`.currencies_id
- `commission_tiers`.threshold_amount_currency_id → `currencies`.currencies_id
- `commissions`.approved_by_user_id → `users`.users_id
- `commissions`.collection_tier_id → `collection_commission_tiers`.collection_commission_tiers_id
- `commissions`.commission_tier_id → `commission_tiers`.commission_tiers_id
- `commissions`.currency_id → `currencies`.currencies_id
- `commissions`.member_id → `members`.members_id
- `commissions`.payment_id → `payments`.payments_id
- `commissions`.payout_payment_id → `payments`.payments_id
- `commissions`.promoter_id → `promoters`.promoters_id
- `company_bank_account_payment_methods`.company_bank_account_id → `company_bank_accounts`.company_bank_accounts_id (ON DELETE CASCADE)
- `company_bank_account_payment_methods`.payment_method_id → `payment_methods`.payment_methods_id
- `company_bank_accounts`.bank_id → `banks`.banks_id
- `company_bank_accounts`.currency_id → `currencies`.currencies_id
- `competitive_commission_award_settlements`.competitive_commission_award_id → `competitive_commission_awards`.competitive_commission_awards_id
- `competitive_commission_award_settlements`.competitive_commission_rule_id → `competitive_commission_rules`.competitive_commission_rules_id
- `competitive_commission_award_settlements`.currency_id → `currencies`.currencies_id
- `competitive_commission_award_settlements`.payout_payment_id → `payments`.payments_id
- `competitive_commission_award_settlements`.promoter_id → `promoters`.promoters_id
- `competitive_commission_awards`.competitive_commission_rule_id → `competitive_commission_rules`.competitive_commission_rules_id
- `competitive_commission_awards`.competitive_commission_rule_position_id → `competitive_commission_rule_positions`.competitive_commission_rule_positions_id
- `competitive_commission_awards`.currency_id → `currencies`.currencies_id
- `competitive_commission_awards`.manual_decision_id → `competitive_commission_manual_decisions`.competitive_commission_manual_decisions_id
- `competitive_commission_awards`.payout_payment_id → `payments`.payments_id
- `competitive_commission_awards`.promoter_id → `promoters`.promoters_id
- `competitive_commission_manual_decisions`.competitive_commission_rule_id → `competitive_commission_rules`.competitive_commission_rules_id
- `competitive_commission_manual_decisions`.competitive_commission_tie_id → `competitive_commission_ties`.competitive_commission_ties_id
- `competitive_commission_manual_decisions`.promoter_id → `promoters`.promoters_id
- `competitive_commission_manual_decisions`.replaced_promoter_id → `promoters`.promoters_id
- `competitive_commission_rule_positions`.competitive_commission_rule_id → `competitive_commission_rules`.competitive_commission_rules_id (ON DELETE CASCADE)
- `competitive_commission_rule_positions`.reward_currency_id → `currencies`.currencies_id
- `competitive_commission_rule_promoter_types`.competitive_commission_rule_id → `competitive_commission_rules`.competitive_commission_rules_id (ON DELETE CASCADE)
- `competitive_commission_rule_promoter_types`.promoter_type_id → `promoter_types`.promoter_types_id
- `competitive_commission_rule_ranks`.competitive_commission_rule_id → `competitive_commission_rules`.competitive_commission_rules_id (ON DELETE CASCADE)
- `competitive_commission_rule_ranks`.rank_id → `promoter_ranks`.promoter_ranks_id
- `competitive_commission_rules`.campaign_id → `campaigns`.campaigns_id
- `competitive_commission_rules`.threshold_currency_id → `currencies`.currencies_id
- `competitive_commission_tie_candidates`.competitive_commission_tie_id → `competitive_commission_ties`.competitive_commission_ties_id (ON DELETE CASCADE)
- `competitive_commission_tie_candidates`.promoter_id → `promoters`.promoters_id
- `competitive_commission_ties`.competitive_commission_rule_id → `competitive_commission_rules`.competitive_commission_rules_id
- `corporate_contracts`.contact_user_id → `users`.users_id
- `corporate_contracts`.plan_id → `plans`.plans_id
- `countries`.official_currency_id → `currencies`.currencies_id
- `data_change_audit_log`.actor_id → `users`.users_id
- `data_change_audit_log`.login_audit_log_id → `login_audit_log`.login_audit_log_id
- `data_change_audit_log`.restored_from_id → `data_change_audit_log`.data_change_audit_log_id
- `exchange_rates`.base_currency_id → `currencies`.currencies_id
- `exchange_rates`.quote_currency_id → `currencies`.currencies_id
- `file_download_links`.created_by → `users`.users_id
- `hierarchy_override_tiers`.campaign_id → `campaigns`.campaigns_id
- `hierarchy_override_tiers`.flat_amount_currency_id → `currencies`.currencies_id
- `hierarchy_override_tiers`.rank_id → `promoter_ranks`.promoter_ranks_id
- `hierarchy_override_tiers`.threshold_amount_currency_id → `currencies`.currencies_id
- `holidays`.city_id → `cities`.cities_id
- `holidays`.country_id → `countries`.countries_id
- `holidays`.state_id → `states`.states_id
- `login_audit_log`.active_role_id → `roles`.roles_id
- `login_audit_log`.user_id → `users`.users_id
- `media_items`.attached_file_id → `attached_files`.attached_files_id (ON DELETE SET NULL)
- `medical_record_access_log`.accessed_by_user_id → `users`.users_id
- `medical_record_access_log`.ally_id → `allies`.allies_id
- `medical_record_access_log`.link_id → `ally_patient_links`.ally_patient_links_id
- `medical_record_access_log`.member_id → `members`.members_id
- `medical_records`.person_id → `persons`.persons_id
- `member_documents`.member_id → `members`.members_id (ON DELETE CASCADE)
- `member_documents`.uploaded_by → `users`.users_id
- `member_promoter_assignments`.actor_user_id → `users`.users_id
- `member_promoter_assignments`.from_promoter_id → `promoters`.promoters_id
- `member_promoter_assignments`.member_id → `members`.members_id
- `member_promoter_assignments`.to_promoter_id → `promoters`.promoters_id
- `members`.corporate_contract_id → `corporate_contracts`.corporate_contracts_id
- `members`.occupation_id → `occupations`.occupations_id
- `members`.person_id → `persons`.persons_id
- `members`.promoter_id → `promoters`.promoters_id
- `membership_charges`.covered_by_payment_id → `payments`.payments_id
- `membership_charges`.currency_id → `currencies`.currencies_id
- `membership_charges`.membership_id → `memberships`.memberships_id
- `membership_charges`.membership_promotion_id → `membership_promotions`.membership_promotions_id
- `membership_promotions`.assigned_by_user_id → `users`.users_id
- `membership_promotions`.code_owner_ally_id → `allies`.allies_id
- `membership_promotions`.code_owner_member_id → `members`.members_id
- `membership_promotions`.code_owner_promoter_id → `promoters`.promoters_id
- `membership_promotions`.membership_id → `memberships`.memberships_id
- `membership_promotions`.promotion_id → `promotions`.promotions_id
- `memberships`.campaign_id → `campaigns`.campaigns_id
- `memberships`.currency_id → `currencies`.currencies_id
- `memberships`.member_id → `members`.members_id
- `memberships`.plan_id → `plans`.plans_id
- `notifications`.recipient_user_id → `users`.users_id
- `organizations`.official_currency_id → `currencies`.currencies_id
- `organizations`.reference_currency_id → `currencies`.currencies_id
- `payer_bank_account_payment_methods`.payer_bank_account_id → `payer_bank_accounts`.payer_bank_accounts_id (ON DELETE CASCADE)
- `payer_bank_account_payment_methods`.payment_method_id → `payment_methods`.payment_methods_id
- `payer_bank_accounts`.bank_id → `banks`.banks_id
- `payer_bank_accounts`.person_id → `persons`.persons_id
- `payment_credit_claims`.opened_by → `users`.users_id
- `payment_credit_claims`.payment_id → `payments`.payments_id
- `payment_credit_claims`.resolved_by → `users`.users_id
- `payment_lines`.bank_id → `banks`.banks_id
- `payment_lines`.company_bank_account_id → `company_bank_accounts`.company_bank_accounts_id
- `payment_lines`.currency_id → `currencies`.currencies_id
- `payment_lines`.payment_id → `payments`.payments_id (ON DELETE CASCADE)
- `payment_lines`.payment_type_id → `payment_methods`.payment_methods_id
- `payment_lines`.payout_account_id → `promoter_payout_accounts`.promoter_payout_accounts_id
- `payment_lines`.reviewed_by → `users`.users_id
- `payments`.beneficiary_person_id → `persons`.persons_id
- `payments`.campaign_id → `campaigns`.campaigns_id
- `payments`.corporate_contract_id → `corporate_contracts`.corporate_contracts_id
- `payments`.currency_id → `currencies`.currencies_id
- `payments`.discounted_by → `users`.users_id
- `payments`.membership_id → `memberships`.memberships_id
- `payments`.payer_user_id → `users`.users_id
- `payments`.payment_type_id → `payment_categories`.payment_categories_id
- `payments`.person_id → `persons`.persons_id
- `payments`.promoter_id → `promoters`.promoters_id
- `payments`.reviewed_by → `users`.users_id
- `permissions`.domain_id → `permission_domains`.permission_domains_id
- `person_credit_entries`.charge_id → `membership_charges`.membership_charges_id
- `person_credit_entries`.converted_currency_id → `currencies`.currencies_id
- `person_credit_entries`.currency_id → `currencies`.currencies_id
- `person_credit_entries`.membership_id → `memberships`.memberships_id
- `person_credit_entries`.payment_id → `payments`.payments_id
- `person_credit_entries`.person_id → `persons`.persons_id
- `person_professional_licenses`.licensing_body_id → `licensing_bodies`.licensing_bodies_id
- `person_professional_licenses`.person_id → `persons`.persons_id
- `persons`.city_id → `cities`.cities_id
- `persons`.gender_id → `genders`.genders_id
- `persons`.marital_status_id → `marital_statuses`.marital_statuses_id
- `plans`.currency_id → `currencies`.currencies_id
- `products`.owner_ally_id → `allies`.allies_id (ON DELETE CASCADE)
- `products`.product_category_id → `product_categories`.product_categories_id
- `professions`.parent_id → `professions`.professions_id
- `promoter_bonus_awards`.bonus_rule_id → `commission_bonus_rules`.commission_bonus_rules_id
- `promoter_bonus_awards`.promoter_id → `promoters`.promoters_id
- `promoter_bonus_awards`.reward_currency_id → `currencies`.currencies_id
- `promoter_hierarchy_overrides`.currency_id → `currencies`.currencies_id
- `promoter_hierarchy_overrides`.payout_payment_id → `payments`.payments_id
- `promoter_hierarchy_overrides`.promoter_id → `promoters`.promoters_id
- `promoter_hierarchy_overrides`.source_commission_id → `commissions`.commissions_id
- `promoter_hierarchy_overrides`.source_override_id → `promoter_hierarchy_overrides`.promoter_hierarchy_overrides_id
- `promoter_hierarchy_overrides`.tier_id → `hierarchy_override_tiers`.hierarchy_override_tiers_id
- `promoter_member_contacts`.member_id → `members`.members_id
- `promoter_member_contacts`.promised_currency_id → `currencies`.currencies_id
- `promoter_member_contacts`.promoter_id → `promoters`.promoters_id
- `promoter_payout_account_payment_methods`.payment_method_id → `payment_methods`.payment_methods_id
- `promoter_payout_account_payment_methods`.promoter_payout_account_id → `promoter_payout_accounts`.promoter_payout_accounts_id (ON DELETE CASCADE)
- `promoter_payout_accounts`.bank_id → `banks`.banks_id
- `promoter_payout_accounts`.promoter_id → `promoters`.promoters_id (ON DELETE CASCADE)
- `promoter_ranks`.parent_rank_id → `promoter_ranks`.promoter_ranks_id
- `promoter_references`.promoter_id → `promoters`.promoters_id (ON DELETE CASCADE)
- `promoter_supervisor_assignments`.actor_user_id → `users`.users_id
- `promoter_supervisor_assignments`.from_supervisor_id → `promoters`.promoters_id
- `promoter_supervisor_assignments`.promoter_id → `promoters`.promoters_id
- `promoter_supervisor_assignments`.to_supervisor_id → `promoters`.promoters_id
- `promoters`.person_id → `persons`.persons_id
- `promoters`.promoter_type_id → `promoter_types`.promoter_types_id
- `promoters`.rank_id → `promoter_ranks`.promoter_ranks_id
- `promoters`.supervisor_id → `promoters`.promoters_id
- `promoters`.user_id → `users`.users_id
- `promotion_plans`.plan_id → `plans`.plans_id
- `promotion_plans`.promotion_id → `promotions`.promotions_id (ON DELETE CASCADE)
- `promotions`.campaign_id → `campaigns`.campaigns_id
- `promotions`.discount_currency_id → `currencies`.currencies_id
- `referrals`.referred_member_id → `members`.members_id
- `referrals`.referrer_member_id → `members`.members_id
- `referrals`.reward_currency_id → `currencies`.currencies_id
- `referrals`.reward_payment_id → `payments`.payments_id
- `report_audit_log`.actor_id → `users`.users_id
- `report_audit_log`.attached_file_id → `attached_files`.attached_files_id
- `report_audit_log`.login_audit_log_id → `login_audit_log`.login_audit_log_id
- `report_share`.created_by → `users`.users_id
- `report_share`.report_audit_log_id → `report_audit_log`.report_audit_log_id
- `role_permissions`.permission_id → `permissions`.permissions_id (ON DELETE CASCADE)
- `role_permissions`.role_id → `roles`.roles_id
- `scheduled_job_runs`.scheduled_job_id → `scheduled_jobs`.scheduled_jobs_id
- `states`.country_id → `countries`.countries_id
- `subsidies`.authorized_by → `users`.users_id
- `subsidies`.member_id → `members`.members_id
- `subsidy_audit_log`.actor_id → `users`.users_id
- `subsidy_audit_log`.subsidy_id → `subsidies`.subsidies_id
- `subsidy_beneficiaries`.beneficiary_id → `beneficiaries`.beneficiaries_id
- `subsidy_beneficiaries`.subsidy_id → `subsidies`.subsidies_id (ON DELETE CASCADE)
- `terms_acceptances`.ally_agreement_id → `ally_agreements`.ally_agreements_id
- `terms_acceptances`.membership_id → `memberships`.memberships_id
- `terms_acceptances`.terms_versions_id → `terms_versions`.terms_versions_id
- `terms_acceptances`.user_id → `users`.users_id
- `terms_versions`.ally_id → `allies`.allies_id
- `terms_versions`.ally_type_id → `ally_types`.ally_types_id
- `user_password_history`.user_id → `users`.users_id
- `user_roles`.role_id → `roles`.roles_id
- `user_roles`.user_id → `users`.users_id
- `users`.default_role_id → `roles`.roles_id
- `users`.person_id → `persons`.persons_id

# Tables

## affiliation_request_logs

> Bitácora de cada solicitud de afiliación: creación, ediciones, aprobación y rechazo

**Columns**
- `affiliation_request_logs_id` bigint NOT NULL IDENTITY
- `affiliation_request_id` bigint NOT NULL
- `action` character varying(20) NOT NULL — Qué pasó: CREATED, UPDATED, APPROVED o REJECTED
- `from_status` character varying(20)
- `to_status` character varying(20)
- `changes` jsonb — Campos editados: lista de {field, from, to}
- `comment` text — Nota de quien hizo el cambio (en un rechazo, el motivo)
- `actor_user_id` bigint — Usuario que hizo el cambio; vacío cuando lo envió la persona desde la página pública
- `action_at` timestamp with time zone NOT NULL DEFAULT now()

**Constraints**
- PK PRIMARY KEY (affiliation_request_logs_id)
- FK `affiliation_request_logs_actor_user_id_fkey` FOREIGN KEY (actor_user_id) REFERENCES users(users_id)
- FK `affiliation_request_logs_affiliation_request_id_fkey` FOREIGN KEY (affiliation_request_id) REFERENCES affiliation_requests(affiliation_requests_id) ON DELETE CASCADE
- CHECK `ck_affiliation_request_logs_action` CHECK (((action)::text = ANY ((ARRAY['CREATED'::character varying, 'UPDATED'::character varying, 'SUBMITTED'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying])::text[])))

**Indexes**
- `idx_affiliation_request_logs_request` btree (affiliation_request_id, action_at DESC)

## affiliation_requests

> Solicitudes de afiliación desde la página pública (QR del asesor); se revisan y se aprueban o rechazan

**Columns**
- `affiliation_requests_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `first_name` character varying(50) NOT NULL
- `last_name` character varying(50) NOT NULL
- `document_type` character varying(2) NOT NULL
- `document_number` character varying(20) NOT NULL
- `birth_date` date
- `email` character varying(254) NOT NULL
- `phone` character varying(30)
- `locale` character varying(10) — Idioma del formulario (es / en), usado para los correos de la solicitud
- `plan_id` bigint
- `promoter_id` bigint — Asesor de la solicitud: el del código de referido (?ref=) en la página pública, o el asesor que la llenó en una afiliación asistida
- `referral_code` character varying(20) — Código de referido tal como llegó, normalizado en mayúsculas
- `password_hash` character varying(255) — Hash BCrypt de la contraseña elegida en el formulario; pasa al usuario al aprobar. Vacío en una afiliación asistida sin contraseña: al aprobar se envía un enlace para crearla
- `terms_versions_id` bigint — Versión del contrato de afiliado aceptada en el formulario
- `terms_rendered_content` text — Texto del contrato tal como lo leyó la persona, con sus datos
- `terms_content_sha256` character varying(64) — SHA-256 del texto aceptado; si al aprobar el texto cambia, se pide aceptar de nuevo al entrar
- `terms_accepted_at` timestamp with time zone — Momento en que la persona aceptó el contrato en el formulario
- `ip_address` character varying(45) — IP desde la que se envió la solicitud (evidencia de la aceptación)
- `user_agent` character varying(400) — Navegador desde el que se envió la solicitud (evidencia de la aceptación)
- `reviewed_by` bigint — Usuario que aprobó o rechazó la solicitud
- `reviewed_at` timestamp with time zone — Momento de la aprobación o el rechazo
- `rejection_reason` text — Motivo del rechazo, obligatorio cuando la solicitud se rechaza; se envía a la persona
- `user_id` bigint — Usuario creado al aprobar
- `member_id` bigint — Afiliado creado al aprobar
- `membership_id` bigint — Membresía creada al aprobar (suspendida hasta el primer pago)
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50) NOT NULL DEFAULT 'PENDING'::character varying — Estado: DRAFT (registro en progreso, aún no enviado), PENDING (por revisar), APPROVED o REJECTED
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid
- `channel` character varying(20) NOT NULL DEFAULT 'SELF_SERVICE'::character varying — Canal de la solicitud: SELF_SERVICE (página pública, QR del asesor) o ASSISTED (el asesor la llenó desde su portal con la familia)
- `expires_at_override` date
- `reinscription_required_override` boolean
- `payment_method_id` bigint — Forma de pago de la inscripción reportada en el formulario; vacío si el solicitante no reportó pago
- `payment_bank_id` bigint — Banco del pago de inscripción reportado (cuando la forma de pago lo exige)
- `payment_amount` numeric(10,2) — Monto del pago de inscripción: la cuota de inscripción del plan al momento de la solicitud (no lo envía el cliente)
- `payment_currency_code` character varying(3) — Moneda (ISO 4217) del pago de inscripción: la moneda del plan
- `payment_identification` character varying(80) — Identificación (cédula/RIF) del titular del pago reportado
- `payment_bank_account_type` character varying(40) — Tipo de cuenta bancaria del pago reportado
- `payment_bank_account_code` character varying(40) — Código de la cuenta bancaria del pago reportado
- `payment_bank_account_identifier` character varying(120) — Número de cuenta bancaria del pago reportado
- `payment_phone` character varying(40) — Teléfono asociado al pago reportado (p. ej. pago móvil)
- `payment_email` character varying(160) — Correo asociado al pago reportado (p. ej. Zelle)
- `payment_reference_number` character varying(80) — Número de referencia del pago reportado
- `payment_date` timestamp with time zone — Fecha en que el solicitante indica haber realizado el pago de inscripción
- `payment_support_file_url` character varying(500) — Clave del comprobante de pago en el almacenamiento (R2); vacía si no se adjuntó o el almacenamiento estaba deshabilitado
- `payment_support_file_name` character varying(255) — Nombre original del comprobante de pago adjuntado
- `payment_support_file_content_type` character varying(100) — Tipo MIME del comprobante de pago adjuntado
- `payment_support_file_size_bytes` bigint — Tamaño en bytes del comprobante de pago adjuntado
- `payment_id` bigint — Pago de inscripción creado al aprobar la solicitud a partir del pago reportado
- `family` jsonb — Familiares capturados en el paso "familia" (lista de {firstName, lastName, documentType, documentNumber, birthDate, relationship, phone, email}); se convierten en beneficiarios al aprobar
- `submitted_at` timestamp with time zone — Momento en que la persona envió el registro a revisión (salió de DRAFT)
- `retain_from_cleanup` boolean NOT NULL DEFAULT false — Registro mantenido (negociación abierta o a la espera de información): la limpieza de borradores abandonados lo omite
- `retain_note` character varying(200) — Nota opcional del motivo por el que se mantiene el registro (máx. 200)
- `retained_by` uuid — UUID del usuario que marcó el registro como mantenido
- `retained_at` timestamp with time zone — Momento en que se marcó el registro como mantenido

**Constraints**
- PK PRIMARY KEY (affiliation_requests_id)
- FK `affiliation_requests_member_id_fkey` FOREIGN KEY (member_id) REFERENCES members(members_id)
- FK `affiliation_requests_membership_id_fkey` FOREIGN KEY (membership_id) REFERENCES memberships(memberships_id)
- FK `affiliation_requests_payment_bank_id_fkey` FOREIGN KEY (payment_bank_id) REFERENCES banks(banks_id)
- FK `affiliation_requests_payment_id_fkey` FOREIGN KEY (payment_id) REFERENCES payments(payments_id)
- FK `affiliation_requests_payment_method_id_fkey` FOREIGN KEY (payment_method_id) REFERENCES payment_methods(payment_methods_id)
- FK `affiliation_requests_plan_id_fkey` FOREIGN KEY (plan_id) REFERENCES plans(plans_id)
- FK `affiliation_requests_promoter_id_fkey` FOREIGN KEY (promoter_id) REFERENCES promoters(promoters_id)
- FK `affiliation_requests_reviewed_by_fkey` FOREIGN KEY (reviewed_by) REFERENCES users(users_id)
- FK `affiliation_requests_terms_versions_id_fkey` FOREIGN KEY (terms_versions_id) REFERENCES terms_versions(terms_versions_id)
- FK `affiliation_requests_user_id_fkey` FOREIGN KEY (user_id) REFERENCES users(users_id)
- UNIQUE `affiliation_requests_uuid_key` UNIQUE (uuid)
- CHECK `chk_affiliation_requests_retain` CHECK ((retain_from_cleanup OR ((retain_note IS NULL) AND (retained_by IS NULL) AND (retained_at IS NULL))))
- CHECK `ck_affiliation_requests_approved_links` CHECK ((((status)::text <> 'APPROVED'::text) OR ((user_id IS NOT NULL) AND (member_id IS NOT NULL) AND (membership_id IS NOT NULL))))
- CHECK `ck_affiliation_requests_channel` CHECK (((channel)::text = ANY ((ARRAY['SELF_SERVICE'::character varying, 'ASSISTED'::character varying])::text[])))
- CHECK `ck_affiliation_requests_complete_after_draft` CHECK ((((status)::text = 'DRAFT'::text) OR ((birth_date IS NOT NULL) AND (plan_id IS NOT NULL) AND (terms_versions_id IS NOT NULL) AND (terms_rendered_content IS NOT NULL) AND (terms_content_sha256 IS NOT NULL) AND (terms_accepted_at IS NOT NULL))))
- CHECK `ck_affiliation_requests_document_type` CHECK (((document_type)::text = ANY ((ARRAY['V'::character varying, 'E'::character varying])::text[])))
- CHECK `ck_affiliation_requests_password_by_channel` CHECK ((((channel)::text = 'ASSISTED'::text) OR (password_hash IS NOT NULL) OR (user_id IS NOT NULL)))
- CHECK `ck_affiliation_requests_payment_method_date` CHECK (((payment_method_id IS NULL) = (payment_date IS NULL)))
- CHECK `ck_affiliation_requests_rejection_reason` CHECK ((((status)::text <> 'REJECTED'::text) OR ((rejection_reason IS NOT NULL) AND (btrim(rejection_reason) <> ''::text))))
- CHECK `ck_affiliation_requests_status` CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying])::text[])))

**Indexes**
- `idx_affiliation_requests_channel` btree (channel)
- `idx_affiliation_requests_payment_id` btree (payment_id) WHERE (payment_id IS NOT NULL)
- `idx_affiliation_requests_plan` btree (plan_id)
- `idx_affiliation_requests_promoter` btree (promoter_id) WHERE (promoter_id IS NOT NULL)
- `idx_affiliation_requests_status_created` btree (status, created_at DESC)
- `idx_affiliation_requests_status_updated` btree (status, updated_at)
- `uq_affiliation_requests_open_user` UNIQUE btree (user_id) WHERE ((user_id IS NOT NULL) AND ((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'PENDING'::character varying])::text[])))
- `uq_affiliation_requests_pending_document` UNIQUE btree (document_type, document_number) WHERE ((status)::text = 'PENDING'::text)
- `uq_affiliation_requests_pending_email` UNIQUE btree (lower((email)::text)) WHERE ((status)::text = 'PENDING'::text)

**Triggers**
- `trg_affiliation_requests_updated_at` BEFORE UPDATE ON app.affiliation_requests FOR EACH ROW EXECUTE FUNCTION set_updated_at()

**Referenced by** `affiliation_request_logs`.(affiliation_request_id)

## allies

**Columns**
- `allies_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `name` character varying(200) NOT NULL
- `tax_document_type` character varying(1)
- `tax_document_number` character varying(20)
- `email` citext
- `phone` character varying(30)
- `website` character varying(255)
- `address` text
- `city_id` bigint
- `logo_url` character varying(500)
- `description` text
- `joined_at` date
- `is_published` boolean NOT NULL DEFAULT false
- `published_at` timestamp with time zone
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50)
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid
- `whatsapp` character varying(30)
- `instagram` character varying(255)
- `facebook` character varying(255)
- `google_maps_url` character varying(500)
- `referral_code` character varying(20)
- `ally_number` integer NOT NULL DEFAULT nextval('allies_ally_number_seq'::regclass) — Número secuencial del aliado (Nº DE ALIADO); asignado por la BD, nunca editable
- `captured_by_promoter_id` bigint — Asesor (promotor) que captó al aliado; su código de referido forma el expediente [CÓDIGO DE ASESOR]-[Nº DE ALIADO]
- `representative_name` character varying(210) — DEPRECATED (V211): use person_id
- `representative_document_type` character varying(1) — DEPRECATED (V211): use person_id
- `representative_document_number` character varying(20) — DEPRECATED (V211): use person_id
- `segment` character varying(3) NOT NULL DEFAULT 'B2B'::character varying — B2B Aliado Comercial / B2P Aliado Profesional (hub ADR 0031); its types must belong to this segment
- `person_id` bigint — Holder person: the professional (B2P) or the representative who signs the agreement (B2B)
- `host_ally_id` bigint — B2P: clinic where the professional practices, when that clinic is an ally too
- `host_name` character varying(200) — B2P: clinic name when it is not an ally
- `office_number` character varying(20) — B2P: office (consultorio) number
- `appointment_phone` character varying(30) — B2P: phone for appointments
- `assistant_name` character varying(150) — B2P: assistant name
- `commercial_zone` character varying(100) — B2B: commercial region / zone
- `trade_name` character varying(200) — B2B: trade name (nombre comercial); name is the legal name
- `founded_year` smallint — B2B: founding year
- `cash_registers_count` integer — B2B: number of cash registers
- `branches_count` integer — B2B: number of branches
- `has_pos` boolean — B2B: whether it runs a POS system
- `representative_position` character varying(100) — B2B: position (cargo) of the representative who signs
- `rating_avg` numeric(3,2) — Promedio de ally_rating de sus consumos vigentes; NULL sin votos. Lo sincroniza el servicio de calificaciones.
- `rating_count` integer NOT NULL DEFAULT 0
- `registration_status` character varying(10) NOT NULL — Ciclo de la solicitud de alianza (ADR 0036): PENDING hasta que un operador registra el convenio y la aprueba; solo APPROVED puede publicarse
- `registration_channel` character varying(12) NOT NULL — Origen del aliado: ADMIN (alta desde el panel), SELF_SERVICE (/solicitar-alianza), IMPORT (plantilla Excel/CSV), ADVISOR (captado por un asesor desde su portal)
- `registration_note` text — Beneficio que el aliado propone al registrarse (texto libre para el operador)
- `approved_at` timestamp with time zone
- `approved_by` uuid
- `rejected_at` timestamp with time zone
- `rejected_by` uuid
- `rejection_reason` text
- `registration_submitted_at` timestamp with time zone — Momento en que el aliado envió su solicitud a revisión; vacío mientras la completa por pasos
- `retain_from_cleanup` boolean NOT NULL DEFAULT false — Registro en progreso mantenido (negociación abierta o a la espera de información): la limpieza de borradores abandonados lo omite
- `retain_note` character varying(200) — Nota opcional del motivo por el que se mantiene el registro (máx. 200)
- `retained_by` uuid — UUID del usuario que marcó el registro como mantenido
- `retained_at` timestamp with time zone — Momento en que se marcó el registro como mantenido

**Constraints**
- PK PRIMARY KEY (allies_id)
- FK `allies_captured_by_promoter_id_fkey` FOREIGN KEY (captured_by_promoter_id) REFERENCES promoters(promoters_id)
- FK `allies_city_id_fkey` FOREIGN KEY (city_id) REFERENCES cities(cities_id)
- FK `allies_host_ally_id_fkey` FOREIGN KEY (host_ally_id) REFERENCES allies(allies_id)
- FK `allies_person_id_fkey` FOREIGN KEY (person_id) REFERENCES persons(persons_id)
- UNIQUE `allies_uuid_key` UNIQUE (uuid)
- UNIQUE `uq_allies_ally_number` UNIQUE (ally_number)
- CHECK `allies_tax_document_type_check` CHECK (((tax_document_type)::text = ANY ((ARRAY['J'::character varying, 'V'::character varying, 'E'::character varying, 'G'::character varying, 'P'::character varying])::text[])))
- CHECK `chk_allies_branches_count` CHECK (((branches_count IS NULL) OR (branches_count >= 0)))
- CHECK `chk_allies_cash_registers_count` CHECK (((cash_registers_count IS NULL) OR (cash_registers_count >= 0)))
- CHECK `chk_allies_founded_year` CHECK (((founded_year IS NULL) OR (founded_year >= 1800)))
- CHECK `chk_allies_host_not_self` CHECK (((host_ally_id IS NULL) OR (host_ally_id <> allies_id)))
- CHECK `chk_allies_published_requires_approval` CHECK (((NOT is_published) OR ((registration_status)::text = 'APPROVED'::text)))
- CHECK `chk_allies_registration_channel` CHECK (((registration_channel)::text = ANY ((ARRAY['ADMIN'::character varying, 'SELF_SERVICE'::character varying, 'IMPORT'::character varying, 'ADVISOR'::character varying])::text[])))
- CHECK `chk_allies_registration_status` CHECK (((registration_status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying])::text[])))
- CHECK `chk_allies_rejection_reason` CHECK ((((registration_status)::text <> 'REJECTED'::text) OR (rejection_reason IS NOT NULL)))
- CHECK `chk_allies_representative_document` CHECK ((((representative_document_type IS NULL) AND (representative_document_number IS NULL)) OR ((representative_document_type IS NOT NULL) AND (representative_document_number IS NOT NULL))))
- CHECK `chk_allies_representative_document_type` CHECK (((representative_document_type IS NULL) OR ((representative_document_type)::text = ANY ((ARRAY['J'::character varying, 'V'::character varying, 'E'::character varying, 'G'::character varying, 'P'::character varying])::text[]))))
- CHECK `chk_allies_retain` CHECK ((retain_from_cleanup OR ((retain_note IS NULL) AND (retained_by IS NULL) AND (retained_at IS NULL))))
- CHECK `chk_allies_segment` CHECK (((segment)::text = ANY ((ARRAY['B2B'::character varying, 'B2P'::character varying])::text[])))
- CHECK `chk_allies_tax_document` CHECK ((((tax_document_type IS NULL) AND (tax_document_number IS NULL)) OR ((tax_document_type IS NOT NULL) AND (tax_document_number IS NOT NULL))))

**Indexes**
- `idx_allies_captured_by_promoter` btree (captured_by_promoter_id) WHERE (captured_by_promoter_id IS NOT NULL)
- `idx_allies_city` btree (city_id) WHERE (city_id IS NOT NULL)
- `idx_allies_host_ally_id` btree (host_ally_id) WHERE (host_ally_id IS NOT NULL)
- `idx_allies_name_unaccent` gin (immutable_unaccent(lower((name)::text)) gin_trgm_ops)
- `idx_allies_person_id` btree (person_id) WHERE (person_id IS NOT NULL)
- `idx_allies_registration_pending` btree (created_at DESC) WHERE ((registration_status)::text = 'PENDING'::text)
- `idx_allies_segment` btree (segment)
- `uniq_allies_tax_document` UNIQUE btree (tax_document_type, tax_document_number) WHERE ((tax_document_type IS NOT NULL) AND (tax_document_number IS NOT NULL))
- `uq_allies_referral_code` UNIQUE btree (referral_code) WHERE (referral_code IS NOT NULL)

**Triggers**
- `trg_allies_updated_at` BEFORE UPDATE ON app.allies FOR EACH ROW EXECUTE FUNCTION set_updated_at()

**Referenced by** `allies`.(host_ally_id), `ally_agreements`.(ally_id), `ally_ally_types`.(ally_id), `ally_opening_hours`.(ally_id), `ally_patient_links`.(created_at_ally_id), `ally_products`.(ally_id), `ally_professions`.(ally_id), `ally_users`.(ally_id), `benefit_usages`.(ally_id), `medical_record_access_log`.(ally_id), `membership_promotions`.(code_owner_ally_id), `products`.(owner_ally_id), `terms_versions`.(ally_id)

## ally_agreements

**Columns**
- `ally_agreements_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `ally_id` bigint NOT NULL
- `agreement_type` character varying(50) NOT NULL
- `start_date` date NOT NULL
- `end_date` date
- `terms` text
- `signed_pdf_url` character varying(500)
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50) NOT NULL DEFAULT 'ACTIVE'::character varying
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid
- `terms_version_id` bigint — Versión del convenio estándar (terms_versions con term_type = ALIADO) bajo la que se firmó
- `auto_renew` boolean NOT NULL DEFAULT true — Renovación automática al vencer la vigencia (por defecto anual y automática)
- `notice_days` integer NOT NULL DEFAULT 30 — Días de preaviso requeridos para rescindir el convenio (por defecto 30)

**Constraints**
- PK PRIMARY KEY (ally_agreements_id)
- FK `ally_agreements_ally_id_fkey` FOREIGN KEY (ally_id) REFERENCES allies(allies_id)
- FK `ally_agreements_terms_version_id_fkey` FOREIGN KEY (terms_version_id) REFERENCES terms_versions(terms_versions_id)
- UNIQUE `ally_agreements_uuid_key` UNIQUE (uuid)
- CHECK `ally_agreements_agreement_type_check` CHECK (((agreement_type)::text = ANY ((ARRAY['COMMERCIAL'::character varying, 'MEDICAL'::character varying, 'EXCLUSIVITY'::character varying, 'SUPPLY'::character varying])::text[])))
- CHECK `ally_agreements_status_check` CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'ACTIVE'::character varying, 'EXPIRED'::character varying, 'TERMINATED'::character varying])::text[])))
- CHECK `chk_agreement_dates` CHECK (((end_date IS NULL) OR (end_date >= start_date)))
- CHECK `chk_ally_agreements_notice_days` CHECK ((notice_days >= 0))

**Indexes**
- `idx_ally_agreements_ally_status` btree (ally_id, status)
- `idx_ally_agreements_terms_version` btree (terms_version_id) WHERE (terms_version_id IS NOT NULL)

**Triggers**
- `trg_ally_agreements_updated_at` BEFORE UPDATE ON app.ally_agreements FOR EACH ROW EXECUTE FUNCTION set_updated_at()

**Referenced by** `terms_acceptances`.(ally_agreement_id)

## ally_ally_types

**Columns**
- `ally_ally_types_id` bigint NOT NULL IDENTITY
- `ally_id` bigint NOT NULL
- `ally_type_id` bigint NOT NULL
- `is_active` boolean NOT NULL DEFAULT true
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid

**Constraints**
- PK PRIMARY KEY (ally_ally_types_id)
- FK `ally_ally_types_ally_id_fkey` FOREIGN KEY (ally_id) REFERENCES allies(allies_id) ON DELETE CASCADE
- FK `ally_ally_types_ally_type_id_fkey` FOREIGN KEY (ally_type_id) REFERENCES ally_types(ally_types_id)
- UNIQUE `ally_ally_types_ally_id_ally_type_id_key` UNIQUE (ally_id, ally_type_id)

**Indexes**
- `idx_ally_ally_types_ally_type` btree (ally_type_id)

## ally_opening_hours

> Horario semanal de atención del aliado: un tramo por fila, varios por día (ADR 0031 §7)

**Columns**
- `ally_opening_hours_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `ally_id` bigint NOT NULL
- `day_of_week` smallint NOT NULL — Día ISO-8601: 1 = lunes … 7 = domingo
- `opens_at` time without time zone NOT NULL — Hora de apertura (hora local de America/Caracas)
- `closes_at` time without time zone NOT NULL — Hora de cierre (hora local de America/Caracas), siempre mayor que la apertura
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50) NOT NULL DEFAULT 'ACTIVE'::character varying
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid

**Constraints**
- PK PRIMARY KEY (ally_opening_hours_id)
- FK `ally_opening_hours_ally_id_fkey` FOREIGN KEY (ally_id) REFERENCES allies(allies_id) ON DELETE CASCADE
- UNIQUE `ally_opening_hours_uuid_key` UNIQUE (uuid)
- UNIQUE `uq_ally_opening_hours_start` UNIQUE (ally_id, day_of_week, opens_at)
- CHECK `ck_ally_opening_hours_day` CHECK (((day_of_week >= 1) AND (day_of_week <= 7)))
- CHECK `ck_ally_opening_hours_range` CHECK ((closes_at > opens_at))
- CHECK `ck_ally_opening_hours_status` CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying])::text[])))

**Indexes**
- `idx_ally_opening_hours_day` btree (day_of_week, opens_at, closes_at)

**Triggers**
- `trg_ally_opening_hours_updated_at` BEFORE UPDATE ON app.ally_opening_hours FOR EACH ROW EXECUTE FUNCTION set_updated_at()

## ally_patient_links

> Vínculo médico–paciente (ADR 0032): el médico (persona titular de un aliado B2P de área médica) puede leer la historia médica del afiliado mientras esté ACTIVE.

**Columns**
- `ally_patient_links_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `doctor_person_id` bigint NOT NULL
- `member_id` bigint NOT NULL
- `created_at_ally_id` bigint NOT NULL
- `created_by_user_id` bigint NOT NULL
- `revoked_at` timestamp with time zone
- `revoked_by` uuid
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50) NOT NULL DEFAULT 'ACTIVE'::character varying
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid

**Constraints**
- PK PRIMARY KEY (ally_patient_links_id)
- FK `ally_patient_links_created_at_ally_id_fkey` FOREIGN KEY (created_at_ally_id) REFERENCES allies(allies_id)
- FK `ally_patient_links_created_by_user_id_fkey` FOREIGN KEY (created_by_user_id) REFERENCES users(users_id)
- FK `ally_patient_links_doctor_person_id_fkey` FOREIGN KEY (doctor_person_id) REFERENCES persons(persons_id)
- FK `ally_patient_links_member_id_fkey` FOREIGN KEY (member_id) REFERENCES members(members_id)
- UNIQUE `ally_patient_links_uuid_key` UNIQUE (uuid)
- CHECK `chk_ally_patient_links_revoked` CHECK ((((status)::text = 'REVOKED'::text) = (revoked_at IS NOT NULL)))
- CHECK `chk_ally_patient_links_status` CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'REVOKED'::character varying])::text[])))

**Indexes**
- `idx_ally_patient_links_member` btree (member_id)
- `uniq_ally_patient_links_active` UNIQUE btree (doctor_person_id, member_id) WHERE ((status)::text = 'ACTIVE'::text)

**Triggers**
- `trg_ally_patient_links_updated_at` BEFORE UPDATE ON app.ally_patient_links FOR EACH ROW EXECUTE FUNCTION set_updated_at()

**Referenced by** `medical_record_access_log`.(link_id)

## ally_product_review_log

**Columns**
- `ally_product_review_log_id` bigint NOT NULL IDENTITY
- `ally_product_id` bigint NOT NULL
- `from_status` character varying(20)
- `to_status` character varying(20) NOT NULL
- `actor_user_id` bigint
- `action_at` timestamp with time zone NOT NULL DEFAULT now()
- `comment` text

**Constraints**
- PK PRIMARY KEY (ally_product_review_log_id)
- FK `ally_product_review_log_actor_user_id_fkey` FOREIGN KEY (actor_user_id) REFERENCES users(users_id)
- FK `ally_product_review_log_ally_product_id_fkey` FOREIGN KEY (ally_product_id) REFERENCES ally_products(ally_products_id) ON DELETE CASCADE
- CHECK `ally_product_review_log_to_status_check` CHECK (((to_status)::text = ANY ((ARRAY['PROPOSED'::character varying, 'IN_REVIEW'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying, 'REMOVED'::character varying])::text[])))

**Indexes**
- `idx_ally_product_review_log_product` btree (ally_product_id, action_at DESC)

## ally_products

> Oferta de un aliado sobre un producto del maestro (antes ally_services, ADR 0026): precio, descuento, exclusiones, revisión y publicación.

**Columns**
- `ally_products_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `ally_id` bigint NOT NULL
- `price_amount` numeric(10,2)
- `discount_pct` numeric(5,2)
- `review_status` character varying(20) NOT NULL DEFAULT 'PROPOSED'::character varying
- `reviewed_by` bigint
- `reviewed_at` timestamp with time zone
- `review_reason` text
- `is_published` boolean NOT NULL DEFAULT false
- `published_at` timestamp with time zone
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50)
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid
- `price_currency_id` bigint
- `exclusions` text — Exclusiones operativas de la tarifa preferencial (p. ej. ofertas existentes, licores, cigarrillos, insumos de alto costo)
- `product_id` bigint NOT NULL
- `logo_url` character varying(500) — URL de la imagen principal propia de la oferta (media_items); NULL = usa la del producto (ADR 0027).
- `discount_type` character varying(20) NOT NULL DEFAULT 'PERCENTAGE'::character varying — Tarifa preferencial: FLAT (monto fijo en la moneda del precio) o PERCENTAGE (porcentaje)
- `discount_amount` numeric(10,2) — Monto de descuento en la moneda del precio (price_currency_id); solo para FLAT
- `rating_avg` numeric(3,2) — Promedio de product_rating de los consumos vigentes de esta oferta; NULL sin votos.
- `rating_count` integer NOT NULL DEFAULT 0

**Constraints**
- PK PRIMARY KEY (ally_products_id)
- FK `ally_products_ally_id_fkey` FOREIGN KEY (ally_id) REFERENCES allies(allies_id) ON DELETE CASCADE
- FK `ally_products_price_currency_id_fkey` FOREIGN KEY (price_currency_id) REFERENCES currencies(currencies_id)
- FK `ally_products_product_id_fkey` FOREIGN KEY (product_id) REFERENCES products(products_id)
- FK `ally_products_reviewed_by_fkey` FOREIGN KEY (reviewed_by) REFERENCES users(users_id)
- UNIQUE `ally_products_uuid_key` UNIQUE (uuid)
- UNIQUE `uq_ally_products_ally_product` UNIQUE (ally_id, product_id)
- CHECK `ally_products_discount_pct_check` CHECK (((discount_pct IS NULL) OR ((discount_pct >= (0)::numeric) AND (discount_pct <= (100)::numeric))))
- CHECK `ally_products_review_status_check` CHECK (((review_status)::text = ANY ((ARRAY['PROPOSED'::character varying, 'IN_REVIEW'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying, 'REMOVED'::character varying])::text[])))
- CHECK `chk_ally_products_discount_coherence` CHECK (((((discount_type)::text = 'FLAT'::text) AND (discount_amount IS NOT NULL) AND (discount_amount > (0)::numeric) AND (discount_pct IS NULL) AND (price_amount IS NOT NULL)) OR (((discount_type)::text = 'PERCENTAGE'::text) AND (discount_amount IS NULL))))
- CHECK `chk_ally_products_discount_type` CHECK (((discount_type)::text = ANY ((ARRAY['FLAT'::character varying, 'PERCENTAGE'::character varying])::text[])))
- CHECK `chk_ally_products_price_currency_paired` CHECK ((((price_amount IS NULL) AND (price_currency_id IS NULL)) OR ((price_amount IS NOT NULL) AND (price_currency_id IS NOT NULL))))
- CHECK `chk_ally_products_published_requires_approved` CHECK (((is_published = false) OR ((review_status)::text = 'APPROVED'::text)))
- CHECK `chk_ally_products_review_reason` CHECK ((((review_status)::text <> ALL ((ARRAY['REJECTED'::character varying, 'REMOVED'::character varying])::text[])) OR ((review_reason IS NOT NULL) AND (reviewed_by IS NOT NULL))))

**Indexes**
- `idx_ally_products_pending_queue` btree (created_at) WHERE ((review_status)::text = ANY ((ARRAY['PROPOSED'::character varying, 'IN_REVIEW'::character varying])::text[]))
- `idx_ally_products_product` btree (product_id)
- `idx_ally_products_published` btree (ally_id) WHERE (is_published AND ((review_status)::text = 'APPROVED'::text))
- `idx_ally_products_review_status` btree (review_status)

**Triggers**
- `trg_ally_products_exclusive_owner` BEFORE INSERT OR UPDATE OF ally_id, product_id ON app.ally_products FOR EACH ROW EXECUTE FUNCTION enforce_exclusive_product_owner()
- `trg_ally_products_updated_at` BEFORE UPDATE ON app.ally_products FOR EACH ROW EXECUTE FUNCTION set_updated_at()

**Referenced by** `ally_product_review_log`.(ally_product_id), `benefit_usages`.(ally_product_id)

## ally_professions

**Columns**
- `ally_specialties_id` bigint NOT NULL IDENTITY
- `ally_id` bigint NOT NULL
- `profession_id` bigint NOT NULL
- `is_active` boolean NOT NULL DEFAULT true
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid

**Constraints**
- PK PRIMARY KEY (ally_specialties_id)
- FK `ally_specialties_ally_id_fkey` FOREIGN KEY (ally_id) REFERENCES allies(allies_id) ON DELETE CASCADE
- FK `ally_specialties_medical_specialty_id_fkey` FOREIGN KEY (profession_id) REFERENCES professions(professions_id)
- UNIQUE `ally_professions_ally_id_profession_id_key` UNIQUE (ally_id, profession_id)

**Indexes**
- `idx_ally_professions_profession` btree (profession_id)

## ally_types

**Columns**
- `ally_types_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `code` character varying(40) NOT NULL
- `name` character varying(100) NOT NULL
- `description` character varying(200)
- `is_active` boolean NOT NULL DEFAULT true
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid
- `segment` character varying(3) NOT NULL DEFAULT 'B2B'::character varying — Segmento del aliado: B2B = Aliado Comercial (persona jurídica), B2P = Aliado Profesional (profesional independiente)
- `professional_area` character varying(20) — Solo para B2P: MEDICAL = Área Médica, WELLNESS = Bienestar Integral; NULL para B2B

**Constraints**
- PK PRIMARY KEY (ally_types_id)
- UNIQUE `ally_types_code_key` UNIQUE (code)
- UNIQUE `ally_types_name_key` UNIQUE (name)
- UNIQUE `ally_types_uuid_key` UNIQUE (uuid)
- CHECK `chk_ally_types_professional_area` CHECK (((professional_area IS NULL) OR ((professional_area)::text = ANY ((ARRAY['MEDICAL'::character varying, 'WELLNESS'::character varying])::text[]))))
- CHECK `chk_ally_types_professional_area_requires_b2p` CHECK (((professional_area IS NULL) OR ((segment)::text = 'B2P'::text)))
- CHECK `chk_ally_types_segment` CHECK (((segment)::text = ANY ((ARRAY['B2B'::character varying, 'B2P'::character varying])::text[])))

**Indexes**
- `idx_ally_types_is_active` btree (is_active) WHERE is_active

**Triggers**
- `trg_ally_types_updated_at` BEFORE UPDATE ON app.ally_types FOR EACH ROW EXECUTE FUNCTION set_updated_at()

**Referenced by** `ally_ally_types`.(ally_type_id), `terms_versions`.(ally_type_id)

## ally_users

**Columns**
- `ally_users_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `ally_id` bigint NOT NULL
- `user_id` bigint NOT NULL
- `ally_role` character varying(20) NOT NULL DEFAULT 'STAFF'::character varying
- `is_primary` boolean NOT NULL DEFAULT false
- `joined_at` date
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50)
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid

**Constraints**
- PK PRIMARY KEY (ally_users_id)
- FK `ally_users_ally_id_fkey` FOREIGN KEY (ally_id) REFERENCES allies(allies_id) ON DELETE CASCADE
- FK `ally_users_user_id_fkey` FOREIGN KEY (user_id) REFERENCES users(users_id)
- UNIQUE `ally_users_ally_id_user_id_key` UNIQUE (ally_id, user_id)
- UNIQUE `ally_users_uuid_key` UNIQUE (uuid)
- CHECK `ally_users_ally_role_check` CHECK (((ally_role)::text = ANY ((ARRAY['OWNER'::character varying, 'STAFF'::character varying, 'VIEWER'::character varying])::text[])))
- CHECK `chk_ally_users_primary_is_owner` CHECK (((is_primary = false) OR ((ally_role)::text = 'OWNER'::text)))

**Indexes**
- `idx_ally_users_user` btree (user_id)
- `uniq_ally_users_one_primary_per_ally` UNIQUE btree (ally_id) WHERE ((is_primary = true) AND (is_active = true))

**Triggers**
- `trg_ally_users_updated_at` BEFORE UPDATE ON app.ally_users FOR EACH ROW EXECUTE FUNCTION set_updated_at()

**Referenced by** `benefit_usages`.(ally_user_id)

## attached_files

**Columns**
- `attached_files_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `owner_table` character varying(50) NOT NULL
- `owner_uuid` uuid NOT NULL
- `visibility` character varying(20) NOT NULL
- `category` character varying(50) NOT NULL
- `file_key` character varying(500) NOT NULL
- `file_name` character varying(255) NOT NULL
- `mime_type` character varying(100) NOT NULL
- `size_bytes` bigint NOT NULL
- `uploaded_by` bigint
- `uploaded_at` timestamp with time zone NOT NULL DEFAULT now()
- `shared` boolean NOT NULL DEFAULT false
- `is_active` boolean NOT NULL DEFAULT true
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid
- `published_at` timestamp with time zone
- `published_by` bigint
- `status` character varying(50)

**Constraints**
- PK PRIMARY KEY (attached_files_id)
- FK `attached_files_published_by_fkey` FOREIGN KEY (published_by) REFERENCES users(users_id)
- FK `attached_files_uploaded_by_fkey` FOREIGN KEY (uploaded_by) REFERENCES users(users_id)
- UNIQUE `attached_files_uuid_key` UNIQUE (uuid)
- CHECK `attached_files_size_bytes_check` CHECK ((size_bytes > 0))
- CHECK `attached_files_visibility_check` CHECK (((visibility)::text = ANY ((ARRAY['PUBLIC'::character varying, 'TEMPORARY'::character varying, 'INTERNAL'::character varying, 'CONFIDENTIAL'::character varying])::text[])))

**Indexes**
- `idx_attached_files_owner` btree (owner_table, owner_uuid)
- `idx_attached_files_temporary_stored` btree (uploaded_at, attached_files_id) WHERE (((visibility)::text = 'TEMPORARY'::text) AND is_active)
- `idx_attached_files_uploader` btree (uploaded_by)

**Triggers**
- `trg_attached_files_updated_at` BEFORE UPDATE ON app.attached_files FOR EACH ROW EXECUTE FUNCTION set_updated_at()

**Referenced by** `media_items`.(attached_file_id), `report_audit_log`.(attached_file_id)

## banks

**Columns**
- `banks_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `code` character varying(10) NOT NULL
- `name` character varying(120) NOT NULL
- `short_name` character varying(60) NOT NULL
- `tax_document_type` character varying(1) NOT NULL
- `tax_document_number` character varying(20) NOT NULL
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50) NOT NULL DEFAULT 'ACTIVE'::character varying
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid

**Constraints**
- PK PRIMARY KEY (banks_id)
- UNIQUE `banks_code_key` UNIQUE (code)
- UNIQUE `banks_tax_document_type_tax_document_number_key` UNIQUE (tax_document_type, tax_document_number)
- UNIQUE `banks_uuid_key` UNIQUE (uuid)
- CHECK `banks_tax_document_type_check` CHECK (((tax_document_type)::text = ANY ((ARRAY['J'::character varying, 'G'::character varying])::text[])))

**Indexes**
- `idx_banks_is_active` btree (is_active) WHERE is_active

**Triggers**
- `trg_banks_updated_at` BEFORE UPDATE ON app.banks FOR EACH ROW EXECUTE FUNCTION set_updated_at()

**Referenced by** `affiliation_requests`.(payment_bank_id), `company_bank_accounts`.(bank_id), `payer_bank_accounts`.(bank_id), `payment_lines`.(bank_id), `promoter_payout_accounts`.(bank_id)

## beneficiaries

**Columns**
- `beneficiaries_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `member_id` bigint NOT NULL
- `person_id` bigint NOT NULL
- `relationship` character varying(20) NOT NULL
- `extra_inscription_paid` boolean NOT NULL DEFAULT false
- `inscription_payment_id` bigint
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50)
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid
- `slot_type` character varying(10) NOT NULL DEFAULT 'BASE'::character varying — Cupo que ocupa el beneficiario: BASE (núcleo familiar base, incluido en el plan) o EXTRA (extensión de blindaje, paga inscripción adicional)
- `user_id` bigint — Usuario propio del beneficiario (rol AFILIADO) para entrar al portal y usar los beneficios como el titular; vacío si nunca se le dio acceso. Al quitar el acceso el usuario se desactiva y el vínculo se conserva
- `access_invited_at` timestamp with time zone — Último envío del correo de activación de la cuenta del beneficiario

**Constraints**
- PK PRIMARY KEY (beneficiaries_id)
- FK `beneficiaries_member_id_fkey` FOREIGN KEY (member_id) REFERENCES members(members_id) ON DELETE CASCADE
- FK `beneficiaries_person_id_fkey` FOREIGN KEY (person_id) REFERENCES persons(persons_id)
- FK `beneficiaries_user_id_fkey` FOREIGN KEY (user_id) REFERENCES users(users_id)
- FK `fk_beneficiaries_inscription_payment` FOREIGN KEY (inscription_payment_id) REFERENCES payments(payments_id)
- UNIQUE `beneficiaries_member_id_person_id_key` UNIQUE (member_id, person_id)
- UNIQUE `beneficiaries_uuid_key` UNIQUE (uuid)
- CHECK `beneficiaries_relationship_check` CHECK (((relationship)::text = ANY ((ARRAY['SPOUSE'::character varying, 'CHILD'::character varying, 'PARENT'::character varying, 'SIBLING'::character varying, 'OTHER'::character varying])::text[])))
- CHECK `chk_beneficiaries_slot_type` CHECK (((slot_type)::text = ANY ((ARRAY['BASE'::character varying, 'EXTRA'::character varying])::text[])))

**Indexes**
- `idx_beneficiaries_extra_unpaid` btree (member_id) WHERE ((is_active = true) AND (extra_inscription_paid = false))
- `idx_beneficiaries_member_active` btree (member_id) WHERE (is_active = true)
- `idx_beneficiaries_person` btree (person_id)
- `uq_beneficiaries_user` UNIQUE btree (user_id) WHERE (user_id IS NOT NULL)

**Triggers**
- `trg_beneficiaries_updated_at` BEFORE UPDATE ON app.beneficiaries FOR EACH ROW EXECUTE FUNCTION set_updated_at()

**Referenced by** `benefit_usages`.(beneficiary_id), `subsidy_beneficiaries`.(beneficiary_id)

## benefit_usages

**Columns**
- `benefit_usages_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `membership_id` bigint NOT NULL
- `ally_id` bigint NOT NULL
- `ally_product_id` bigint
- `ally_user_id` bigint
- `usage_date` date NOT NULL DEFAULT CURRENT_DATE
- `usage_datetime` timestamp with time zone NOT NULL DEFAULT now()
- `copay_amount` numeric(10,2)
- `metadata` jsonb
- `notes` text
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50) NOT NULL DEFAULT 'REGISTERED'::character varying
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid
- `copay_currency_id` bigint
- `consumption_amount` numeric(10,2) — Valor del servicio consumido (precio de lista), distinto del copago que pagó el afiliado. NULL en los usos previos a V158.
- `consumption_currency_id` bigint
- `product_category_id` bigint NOT NULL — Rubro del consumo. Se copia del producto de la oferta cuando la hay; si no, lo elige el operador. Obligatorio para que todo consumo entre en el reporte por rubro.
- `beneficiary_id` bigint — Beneficiario que usó el beneficio en el aliado; vacío cuando lo usó el titular
- `ally_rating` smallint — Estrellas 1..5 que el afiliado dio al aliado por este consumo. NULL = sin calificar.
- `product_rating` smallint — Estrellas 1..5 al producto/servicio consumido. Solo cuando hay ally_product_id.
- `rating_comment` character varying(500)
- `rated_at` timestamp with time zone — Última vez que el afiliado calificó (o editó la calificación de) este consumo.
- `converted_currency_id` bigint — Moneda oficial de la organización al registrar el consumo; destino de la conversión (V229).
- `consumption_amount_converted` numeric(14,2)
- `consumption_exchange_rate` numeric(18,8) — Unidades de la moneda convertida por 1 unidad de consumption_currency, vigente en la fecha del consumo (V229).
- `copay_amount_converted` numeric(14,2)
- `copay_exchange_rate` numeric(18,8) — Unidades de la moneda convertida por 1 unidad de copay_currency, vigente en la fecha del consumo (V229).
- `exchange_rate_date` date — Fecha valor (día de vigencia BCV) de la tasa usada en la conversión (V229, V246).

**Constraints**
- PK PRIMARY KEY (benefit_usages_id)
- FK `benefit_usages_ally_id_fkey` FOREIGN KEY (ally_id) REFERENCES allies(allies_id)
- FK `benefit_usages_ally_product_id_fkey` FOREIGN KEY (ally_product_id) REFERENCES ally_products(ally_products_id)
- FK `benefit_usages_ally_user_id_fkey` FOREIGN KEY (ally_user_id) REFERENCES ally_users(ally_users_id)
- FK `benefit_usages_beneficiary_id_fkey` FOREIGN KEY (beneficiary_id) REFERENCES beneficiaries(beneficiaries_id)
- FK `benefit_usages_consumption_currency_id_fkey` FOREIGN KEY (consumption_currency_id) REFERENCES currencies(currencies_id)
- FK `benefit_usages_converted_currency_id_fkey` FOREIGN KEY (converted_currency_id) REFERENCES currencies(currencies_id)
- FK `benefit_usages_copay_currency_id_fkey` FOREIGN KEY (copay_currency_id) REFERENCES currencies(currencies_id)
- FK `benefit_usages_membership_id_fkey` FOREIGN KEY (membership_id) REFERENCES memberships(memberships_id)
- FK `benefit_usages_product_category_id_fkey` FOREIGN KEY (product_category_id) REFERENCES product_categories(product_categories_id)
- UNIQUE `benefit_usages_uuid_key` UNIQUE (uuid)
- CHECK `benefit_usages_status_check` CHECK (((status)::text = ANY ((ARRAY['REGISTERED'::character varying, 'REVERSED'::character varying, 'DISPUTED'::character varying])::text[])))
- CHECK `chk_benefit_usages_ally_rating_range` CHECK (((ally_rating IS NULL) OR ((ally_rating >= 1) AND (ally_rating <= 5))))
- CHECK `chk_benefit_usages_consumption_converted_paired` CHECK (((consumption_amount_converted IS NULL) = (consumption_exchange_rate IS NULL)))
- CHECK `chk_benefit_usages_consumption_paired` CHECK ((((consumption_amount IS NULL) AND (consumption_currency_id IS NULL)) OR ((consumption_amount IS NOT NULL) AND (consumption_amount >= (0)::numeric) AND (consumption_currency_id IS NOT NULL))))
- CHECK `chk_benefit_usages_converted_currency` CHECK ((((consumption_amount_converted IS NULL) AND (copay_amount_converted IS NULL)) OR (converted_currency_id IS NOT NULL)))
- CHECK `chk_benefit_usages_copay_converted_paired` CHECK (((copay_amount_converted IS NULL) = (copay_exchange_rate IS NULL)))
- CHECK `chk_benefit_usages_copay_paired` CHECK ((((copay_amount IS NULL) AND (copay_currency_id IS NULL)) OR ((copay_amount IS NOT NULL) AND (copay_amount >= (0)::numeric) AND (copay_currency_id IS NOT NULL))))
- CHECK `chk_benefit_usages_datetime_aligned` CHECK (((usage_datetime <= (usage_date + '2 days'::interval)) AND (usage_datetime >= (usage_date - '1 day'::interval))))
- CHECK `chk_benefit_usages_not_future` CHECK ((usage_date <= (CURRENT_DATE + '1 day'::interval)))
- CHECK `chk_benefit_usages_product_rating_needs_product` CHECK (((product_rating IS NULL) OR (ally_product_id IS NOT NULL)))
- CHECK `chk_benefit_usages_product_rating_range` CHECK (((product_rating IS NULL) OR ((product_rating >= 1) AND (product_rating <= 5))))
- CHECK `chk_benefit_usages_rating_paired` CHECK ((((rated_at IS NULL) AND (ally_rating IS NULL) AND (product_rating IS NULL) AND (rating_comment IS NULL)) OR ((rated_at IS NOT NULL) AND (ally_rating IS NOT NULL))))

**Indexes**
- `idx_benefit_usages_active_status` btree (status, usage_date DESC) WHERE is_active
- `idx_benefit_usages_ally_date` btree (ally_id, usage_date DESC)
- `idx_benefit_usages_ally_product_date` btree (ally_product_id, usage_date DESC) WHERE (ally_product_id IS NOT NULL)
- `idx_benefit_usages_ally_product_rated` btree (ally_product_id) WHERE ((product_rating IS NOT NULL) AND is_active)
- `idx_benefit_usages_ally_rated` btree (ally_id) WHERE ((ally_rating IS NOT NULL) AND is_active AND ((status)::text = 'REGISTERED'::text))
- `idx_benefit_usages_beneficiary` btree (beneficiary_id, usage_date DESC) WHERE (beneficiary_id IS NOT NULL)
- `idx_benefit_usages_category_date` btree (product_category_id, usage_date DESC) WHERE is_active
- `idx_benefit_usages_membership_date` btree (membership_id, usage_date DESC)
- `idx_benefit_usages_usage_datetime` btree (usage_datetime DESC)

**Triggers**
- `trg_benefit_usages_updated_at` BEFORE UPDATE ON app.benefit_usages FOR EACH ROW EXECUTE FUNCTION set_updated_at()

## campaign_promoters

**Columns**
- `campaign_promoters_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL
- `campaign_id` bigint NOT NULL
- `promoter_id` bigint NOT NULL
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50)
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid

**Constraints**
- PK PRIMARY KEY (campaign_promoters_id)
- FK `campaign_promoters_campaign_id_fkey` FOREIGN KEY (campaign_id) REFERENCES campaigns(campaigns_id)
- FK `campaign_promoters_promoter_id_fkey` FOREIGN KEY (promoter_id) REFERENCES promoters(promoters_id)
- UNIQUE `campaign_promoters_uuid_key` UNIQUE (uuid)
- UNIQUE `uq_campaign_promoters_campaign_promoter` UNIQUE (campaign_id, promoter_id)

**Indexes**
- `idx_campaign_promoters_promoter` btree (promoter_id)

**Triggers**
- `trg_campaign_promoters_updated_at` BEFORE UPDATE ON app.campaign_promoters FOR EACH ROW EXECUTE FUNCTION set_updated_at()

## campaign_transaction_exceptions

**Columns**
- `campaign_transaction_exceptions_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL
- `campaign_id` bigint NOT NULL
- `payment_id` bigint
- `membership_id` bigint
- `action` character varying(10) NOT NULL
- `reason` text
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50)
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid

**Constraints**
- PK PRIMARY KEY (campaign_transaction_exceptions_id)
- FK `campaign_transaction_exceptions_campaign_id_fkey` FOREIGN KEY (campaign_id) REFERENCES campaigns(campaigns_id)
- FK `campaign_transaction_exceptions_membership_id_fkey` FOREIGN KEY (membership_id) REFERENCES memberships(memberships_id)
- FK `campaign_transaction_exceptions_payment_id_fkey` FOREIGN KEY (payment_id) REFERENCES payments(payments_id)
- UNIQUE `campaign_transaction_exceptions_uuid_key` UNIQUE (uuid)
- CHECK `chk_campaign_transaction_exceptions_action` CHECK (((action)::text = ANY ((ARRAY['INCLUDE'::character varying, 'EXCLUDE'::character varying])::text[])))
- CHECK `chk_campaign_transaction_exceptions_one_target` CHECK (((payment_id IS NOT NULL) <> (membership_id IS NOT NULL)))

**Indexes**
- `idx_campaign_transaction_exceptions_campaign` btree (campaign_id)

**Triggers**
- `trg_campaign_transaction_exceptions_updated_at` BEFORE UPDATE ON app.campaign_transaction_exceptions FOR EACH ROW EXECUTE FUNCTION set_updated_at()

## campaign_transaction_links

**Columns**
- `campaign_transaction_links_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL
- `campaign_id` bigint NOT NULL
- `payment_id` bigint
- `membership_id` bigint
- `source` character varying(20) NOT NULL DEFAULT 'AUTO'::character varying
- `resolved_at` timestamp with time zone NOT NULL DEFAULT now()
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50)
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid

**Constraints**
- PK PRIMARY KEY (campaign_transaction_links_id)
- FK `campaign_transaction_links_campaign_id_fkey` FOREIGN KEY (campaign_id) REFERENCES campaigns(campaigns_id)
- FK `campaign_transaction_links_membership_id_fkey` FOREIGN KEY (membership_id) REFERENCES memberships(memberships_id)
- FK `campaign_transaction_links_payment_id_fkey` FOREIGN KEY (payment_id) REFERENCES payments(payments_id)
- UNIQUE `campaign_transaction_links_uuid_key` UNIQUE (uuid)
- CHECK `chk_campaign_transaction_links_one_target` CHECK (((payment_id IS NOT NULL) <> (membership_id IS NOT NULL)))
- CHECK `chk_campaign_transaction_links_source` CHECK (((source)::text = ANY ((ARRAY['AUTO'::character varying, 'EXCEPTION_INCLUDE'::character varying, 'EXCEPTION_EXCLUDE'::character varying])::text[])))

**Indexes**
- `idx_campaign_transaction_links_campaign` btree (campaign_id)
- `idx_campaign_transaction_links_membership` btree (membership_id) WHERE (membership_id IS NOT NULL)
- `idx_campaign_transaction_links_payment` btree (payment_id) WHERE (payment_id IS NOT NULL)

**Triggers**
- `trg_campaign_transaction_links_updated_at` BEFORE UPDATE ON app.campaign_transaction_links FOR EACH ROW EXECUTE FUNCTION set_updated_at()

## campaigns

**Columns**
- `campaigns_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL
- `name` character varying(150) NOT NULL
- `description` text
- `starts_at` timestamp with time zone NOT NULL
- `ends_at` timestamp with time zone NOT NULL
- `enabled` boolean NOT NULL DEFAULT true
- `scope` character varying(20) NOT NULL DEFAULT 'ALL'::character varying
- `mode` character varying(20) NOT NULL DEFAULT 'GENERAL'::character varying
- `evaluate_only_at_end` boolean NOT NULL DEFAULT false
- `pay_only_at_end` boolean NOT NULL DEFAULT false
- `target_amount` numeric(14,2)
- `target_count` integer
- `exclusivity_group` character varying(80)
- `priority` integer
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50)
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid
- `target_amount_currency_id` bigint

**Constraints**
- PK PRIMARY KEY (campaigns_id)
- FK `campaigns_target_amount_currency_id_fkey` FOREIGN KEY (target_amount_currency_id) REFERENCES currencies(currencies_id)
- UNIQUE `campaigns_uuid_key` UNIQUE (uuid)
- CHECK `campaigns_target_amount_currency_xor` CHECK ((((target_amount IS NULL) AND (target_amount_currency_id IS NULL)) OR ((target_amount IS NOT NULL) AND (target_amount_currency_id IS NOT NULL))))
- CHECK `chk_campaigns_dates` CHECK ((ends_at > starts_at))
- CHECK `chk_campaigns_evaluate_end_implies_pay_end` CHECK (((NOT evaluate_only_at_end) OR pay_only_at_end))
- CHECK `chk_campaigns_mode` CHECK (((mode)::text = ANY ((ARRAY['TARGETED'::character varying, 'GENERAL'::character varying])::text[])))
- CHECK `chk_campaigns_scope` CHECK (((scope)::text = ANY ((ARRAY['ALL'::character varying, 'INCLUDE'::character varying, 'EXCLUDE'::character varying])::text[])))

**Indexes**
- `idx_campaigns_active_window` btree (is_active, starts_at, ends_at)
- `idx_campaigns_exclusivity_group` btree (exclusivity_group) WHERE (exclusivity_group IS NOT NULL)

**Triggers**
- `trg_campaigns_updated_at` BEFORE UPDATE ON app.campaigns FOR EACH ROW EXECUTE FUNCTION set_updated_at()

**Referenced by** `campaign_promoters`.(campaign_id), `campaign_transaction_exceptions`.(campaign_id), `campaign_transaction_links`.(campaign_id), `collection_commission_tiers`.(campaign_id), `commission_bonus_rules`.(campaign_id), `commission_tiers`.(campaign_id), `competitive_commission_rules`.(campaign_id), `hierarchy_override_tiers`.(campaign_id), `memberships`.(campaign_id), `payments`.(campaign_id), `promotions`.(campaign_id)

## cities

**Columns**
- `cities_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `state_id` bigint NOT NULL
- `name` character varying(120) NOT NULL
- `is_active` boolean NOT NULL DEFAULT true
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid

**Constraints**
- PK PRIMARY KEY (cities_id)
- FK `cities_state_id_fkey` FOREIGN KEY (state_id) REFERENCES states(states_id)
- UNIQUE `cities_state_id_name_key` UNIQUE (state_id, name)
- UNIQUE `cities_uuid_key` UNIQUE (uuid)

**Indexes**
- `idx_cities_is_active` btree (is_active) WHERE is_active

**Triggers**
- `trg_cities_updated_at` BEFORE UPDATE ON app.cities FOR EACH ROW EXECUTE FUNCTION set_updated_at()

**Referenced by** `allies`.(city_id), `holidays`.(city_id), `persons`.(city_id)

## collection_commission_tier_promoter_types

**Columns**
- `collection_commission_tier_promoter_types_id` bigint NOT NULL IDENTITY
- `collection_commission_tier_id` bigint NOT NULL
- `promoter_type_id` bigint NOT NULL
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid

**Constraints**
- PK PRIMARY KEY (collection_commission_tier_promoter_types_id)
- FK `collection_commission_tier_pr_collection_commission_tier_i_fkey` FOREIGN KEY (collection_commission_tier_id) REFERENCES collection_commission_tiers(collection_commission_tiers_id) ON DELETE CASCADE
- FK `collection_commission_tier_promoter_types_promoter_type_id_fkey` FOREIGN KEY (promoter_type_id) REFERENCES promoter_types(promoter_types_id)
- UNIQUE `collection_commission_tier_pr_collection_commission_tier_id_key` UNIQUE (collection_commission_tier_id, promoter_type_id)

**Indexes**
- `idx_collection_commission_tier_promoter_types_type` btree (promoter_type_id)

## collection_commission_tiers

**Columns**
- `collection_commission_tiers_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL
- `name` character varying(80) NOT NULL
- `max_days` integer
- `commission_pct` numeric(5,2)
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50)
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid
- `description` text
- `basis` character varying(10) NOT NULL DEFAULT 'DAYS'::character varying
- `min_amount` numeric(14,2)
- `flat_amount` numeric(10,2)
- `flat_amount_currency_id` bigint
- `campaign_id` bigint
- `starts_at` timestamp with time zone
- `ends_at` timestamp with time zone
- `min_amount_currency_id` bigint
- `accrual_period_strategy` character varying(20) NOT NULL DEFAULT 'MONTHLY'::character varying
- `partial_settlement_period_strategy` character varying(20) NOT NULL DEFAULT 'MONTHLY'::character varying
- `final_settlement_period_strategy` character varying(20) NOT NULL DEFAULT 'MONTHLY'::character varying
- `retroactive_settlement_period_strategy` character varying(20) NOT NULL DEFAULT 'MONTHLY'::character varying
- `accrual_period_anchor` smallint
- `partial_settlement_period_anchor` smallint
- `final_settlement_period_anchor` smallint
- `retroactive_settlement_period_anchor` smallint

**Constraints**
- PK PRIMARY KEY (collection_commission_tiers_id)
- FK `collection_commission_tiers_campaign_id_fkey` FOREIGN KEY (campaign_id) REFERENCES campaigns(campaigns_id)
- FK `collection_commission_tiers_flat_amount_currency_id_fkey` FOREIGN KEY (flat_amount_currency_id) REFERENCES currencies(currencies_id)
- FK `collection_commission_tiers_min_amount_currency_id_fkey` FOREIGN KEY (min_amount_currency_id) REFERENCES currencies(currencies_id)
- UNIQUE `collection_commission_tiers_uuid_key` UNIQUE (uuid)
- CHECK `chk_collection_commission_tiers_accrual_period_anchor_range` CHECK (((accrual_period_anchor IS NULL) OR ((accrual_period_anchor >= 1) AND (accrual_period_anchor <= 31))))
- CHECK `chk_collection_commission_tiers_accrual_period_strategy` CHECK (((accrual_period_strategy)::text = ANY ((ARRAY['DAILY'::character varying, 'WEEKLY'::character varying, 'BIWEEKLY'::character varying, 'MONTHLY'::character varying, 'QUARTERLY'::character varying, 'SEMIANNUAL'::character varying, 'ANNUAL'::character varying])::text[])))
- CHECK `chk_collection_commission_tiers_basis` CHECK (((basis)::text = ANY ((ARRAY['DAYS'::character varying, 'AMOUNT'::character varying])::text[])))
- CHECK `chk_collection_commission_tiers_basis_field` CHECK (((((basis)::text = 'DAYS'::text) AND (max_days IS NOT NULL) AND (min_amount IS NULL) AND (min_amount_currency_id IS NULL)) OR (((basis)::text = 'AMOUNT'::text) AND (min_amount IS NOT NULL) AND (min_amount_currency_id IS NOT NULL) AND (max_days IS NULL))))
- CHECK `chk_collection_commission_tiers_final_settlement_period_anchor_` CHECK (((final_settlement_period_anchor IS NULL) OR ((final_settlement_period_anchor >= 1) AND (final_settlement_period_anchor <= 31))))
- CHECK `chk_collection_commission_tiers_final_settlement_period_strateg` CHECK (((final_settlement_period_strategy)::text = ANY ((ARRAY['DAILY'::character varying, 'WEEKLY'::character varying, 'BIWEEKLY'::character varying, 'MONTHLY'::character varying, 'QUARTERLY'::character varying, 'SEMIANNUAL'::character varying, 'ANNUAL'::character varying])::text[])))
- CHECK `chk_collection_commission_tiers_flat_needs_currency` CHECK (((flat_amount IS NULL) OR (flat_amount_currency_id IS NOT NULL)))
- CHECK `chk_collection_commission_tiers_max_days` CHECK ((max_days > 0))
- CHECK `chk_collection_commission_tiers_partial_settlement_period_ancho` CHECK (((partial_settlement_period_anchor IS NULL) OR ((partial_settlement_period_anchor >= 1) AND (partial_settlement_period_anchor <= 31))))
- CHECK `chk_collection_commission_tiers_partial_settlement_period_strat` CHECK (((partial_settlement_period_strategy)::text = ANY ((ARRAY['DAILY'::character varying, 'WEEKLY'::character varying, 'BIWEEKLY'::character varying, 'MONTHLY'::character varying, 'QUARTERLY'::character varying, 'SEMIANNUAL'::character varying, 'ANNUAL'::character varying])::text[])))
- CHECK `chk_collection_commission_tiers_pct` CHECK (((commission_pct > (0)::numeric) AND (commission_pct <= (100)::numeric)))
- CHECK `chk_collection_commission_tiers_pct_xor_flat` CHECK (((commission_pct IS NOT NULL) <> (flat_amount IS NOT NULL)))
- CHECK `chk_collection_commission_tiers_retroactive_settlement_period_a` CHECK (((retroactive_settlement_period_anchor IS NULL) OR ((retroactive_settlement_period_anchor >= 1) AND (retroactive_settlement_period_anchor <= 31))))
- CHECK `chk_collection_commission_tiers_retroactive_settlement_period_s` CHECK (((retroactive_settlement_period_strategy)::text = ANY ((ARRAY['DAILY'::character varying, 'WEEKLY'::character varying, 'BIWEEKLY'::character varying, 'MONTHLY'::character varying, 'QUARTERLY'::character varying, 'SEMIANNUAL'::character varying, 'ANNUAL'::character varying])::text[])))

**Indexes**
- `idx_collection_commission_tiers_campaign` btree (campaign_id) WHERE (campaign_id IS NOT NULL)
- `idx_collection_commission_tiers_lookup` btree (max_days) WHERE is_active

**Triggers**
- `trg_collection_commission_tiers_updated_at` BEFORE UPDATE ON app.collection_commission_tiers FOR EACH ROW EXECUTE FUNCTION set_updated_at()

**Referenced by** `collection_commission_tier_promoter_types`.(collection_commission_tier_id), `commissions`.(collection_tier_id)

## commission_bonus_rule_promoter_types

**Columns**
- `commission_bonus_rule_promoter_types_id` bigint NOT NULL IDENTITY
- `commission_bonus_rule_id` bigint NOT NULL
- `promoter_type_id` bigint NOT NULL
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid

**Constraints**
- PK PRIMARY KEY (commission_bonus_rule_promoter_types_id)
- FK `commission_bonus_rule_promoter_ty_commission_bonus_rule_id_fkey` FOREIGN KEY (commission_bonus_rule_id) REFERENCES commission_bonus_rules(commission_bonus_rules_id) ON DELETE CASCADE
- FK `commission_bonus_rule_promoter_types_promoter_type_id_fkey` FOREIGN KEY (promoter_type_id) REFERENCES promoter_types(promoter_types_id)
- UNIQUE `commission_bonus_rule_promote_commission_bonus_rule_id_prom_key` UNIQUE (commission_bonus_rule_id, promoter_type_id)

**Indexes**
- `idx_commission_bonus_rule_promoter_types_type` btree (promoter_type_id)

## commission_bonus_rules

**Columns**
- `commission_bonus_rules_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL
- `name` character varying(150) NOT NULL
- `description` text
- `metric` character varying(30) NOT NULL
- `accrual` character varying(20) NOT NULL
- `threshold_count` integer NOT NULL
- `accrual_period_strategy` character varying(20) NOT NULL
- `campaign_start` timestamp with time zone
- `campaign_end` timestamp with time zone
- `reward_type` character varying(20) NOT NULL
- `flat_amount` numeric(10,2)
- `reward_pct` numeric(5,2)
- `include_system_promoters` boolean NOT NULL DEFAULT false
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50)
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid
- `reward_currency_id` bigint NOT NULL
- `campaign_id` bigint
- `starts_at` timestamp with time zone
- `ends_at` timestamp with time zone
- `threshold_amount` numeric(14,2)
- `threshold_currency_id` bigint
- `partial_settlement_period_strategy` character varying(20) NOT NULL DEFAULT 'MONTHLY'::character varying
- `final_settlement_period_strategy` character varying(20) NOT NULL DEFAULT 'MONTHLY'::character varying
- `retroactive_settlement_period_strategy` character varying(20) NOT NULL DEFAULT 'MONTHLY'::character varying
- `accrual_period_anchor` smallint
- `partial_settlement_period_anchor` smallint
- `final_settlement_period_anchor` smallint
- `retroactive_settlement_period_anchor` smallint

**Constraints**
- PK PRIMARY KEY (commission_bonus_rules_id)
- FK `commission_bonus_rules_campaign_id_fkey` FOREIGN KEY (campaign_id) REFERENCES campaigns(campaigns_id)
- FK `commission_bonus_rules_reward_currency_id_fkey` FOREIGN KEY (reward_currency_id) REFERENCES currencies(currencies_id)
- FK `commission_bonus_rules_threshold_currency_id_fkey` FOREIGN KEY (threshold_currency_id) REFERENCES currencies(currencies_id)
- UNIQUE `commission_bonus_rules_uuid_key` UNIQUE (uuid)
- CHECK `chk_bonus_rule_accrual` CHECK (((accrual)::text = ANY ((ARRAY['PER_BLOCK'::character varying, 'THRESHOLD'::character varying])::text[])))
- CHECK `chk_bonus_rule_accrual_period_strategy` CHECK (((accrual_period_strategy)::text = ANY ((ARRAY['LIFETIME'::character varying, 'DAILY'::character varying, 'WEEKLY'::character varying, 'BIWEEKLY'::character varying, 'MONTHLY'::character varying, 'QUARTERLY'::character varying, 'SEMIANNUAL'::character varying, 'ANNUAL'::character varying, 'CAMPAIGN'::character varying])::text[])))
- CHECK `chk_bonus_rule_campaign_dates` CHECK (((((accrual_period_strategy)::text = 'CAMPAIGN'::text) AND (campaign_start IS NOT NULL) AND (campaign_end IS NOT NULL) AND (campaign_end >= campaign_start)) OR (((accrual_period_strategy)::text <> 'CAMPAIGN'::text) AND (campaign_start IS NULL) AND (campaign_end IS NULL))))
- CHECK `chk_bonus_rule_metric` CHECK (((metric)::text = ANY ((ARRAY['NEW_SUBSCRIBERS'::character varying, 'ACTIVE_SUBSCRIBERS'::character varying, 'AMOUNT_COLLECTED'::character varying])::text[])))
- CHECK `chk_bonus_rule_reward_coherence` CHECK (((((reward_type)::text = 'FLAT'::text) AND (flat_amount IS NOT NULL) AND (flat_amount > (0)::numeric) AND (reward_pct IS NULL)) OR (((reward_type)::text = 'PERCENTAGE'::text) AND (reward_pct IS NOT NULL) AND (reward_pct > (0)::numeric) AND (reward_pct <= (100)::numeric) AND (flat_amount IS NULL))))
- CHECK `chk_bonus_rule_reward_type` CHECK (((reward_type)::text = ANY ((ARRAY['FLAT'::character varying, 'PERCENTAGE'::character varying])::text[])))
- CHECK `chk_bonus_rule_threshold_positive` CHECK ((threshold_count > 0))
- CHECK `chk_commission_bonus_rules_accrual_period_anchor_range` CHECK (((accrual_period_anchor IS NULL) OR ((accrual_period_anchor >= 1) AND (accrual_period_anchor <= 31))))
- CHECK `chk_commission_bonus_rules_final_settlement_period_anchor_range` CHECK (((final_settlement_period_anchor IS NULL) OR ((final_settlement_period_anchor >= 1) AND (final_settlement_period_anchor <= 31))))
- CHECK `chk_commission_bonus_rules_final_settlement_period_strategy` CHECK (((final_settlement_period_strategy)::text = ANY ((ARRAY['DAILY'::character varying, 'WEEKLY'::character varying, 'BIWEEKLY'::character varying, 'MONTHLY'::character varying, 'QUARTERLY'::character varying, 'SEMIANNUAL'::character varying, 'ANNUAL'::character varying])::text[])))
- CHECK `chk_commission_bonus_rules_partial_settlement_period_anchor_ran` CHECK (((partial_settlement_period_anchor IS NULL) OR ((partial_settlement_period_anchor >= 1) AND (partial_settlement_period_anchor <= 31))))
- CHECK `chk_commission_bonus_rules_partial_settlement_period_strategy` CHECK (((partial_settlement_period_strategy)::text = ANY ((ARRAY['DAILY'::character varying, 'WEEKLY'::character varying, 'BIWEEKLY'::character varying, 'MONTHLY'::character varying, 'QUARTERLY'::character varying, 'SEMIANNUAL'::character varying, 'ANNUAL'::character varying])::text[])))
- CHECK `chk_commission_bonus_rules_retroactive_settlement_period_anchor` CHECK (((retroactive_settlement_period_anchor IS NULL) OR ((retroactive_settlement_period_anchor >= 1) AND (retroactive_settlement_period_anchor <= 31))))
- CHECK `chk_commission_bonus_rules_retroactive_settlement_period_strate` CHECK (((retroactive_settlement_period_strategy)::text = ANY ((ARRAY['DAILY'::character varying, 'WEEKLY'::character varying, 'BIWEEKLY'::character varying, 'MONTHLY'::character varying, 'QUARTERLY'::character varying, 'SEMIANNUAL'::character varying, 'ANNUAL'::character varying])::text[])))
- CHECK `commission_bonus_rules_threshold_xor` CHECK (((((metric)::text = 'AMOUNT_COLLECTED'::text) AND (threshold_amount IS NOT NULL) AND (threshold_currency_id IS NOT NULL) AND (threshold_count = 0)) OR (((metric)::text <> 'AMOUNT_COLLECTED'::text) AND (threshold_amount IS NULL) AND (threshold_currency_id IS NULL))))

**Indexes**
- `idx_commission_bonus_rules_active` btree (created_at DESC) WHERE is_active
- `idx_commission_bonus_rules_campaign` btree (campaign_id) WHERE (campaign_id IS NOT NULL)

**Triggers**
- `trg_commission_bonus_rules_updated_at` BEFORE UPDATE ON app.commission_bonus_rules FOR EACH ROW EXECUTE FUNCTION set_updated_at()

**Referenced by** `commission_bonus_rule_promoter_types`.(commission_bonus_rule_id), `promoter_bonus_awards`.(bonus_rule_id)

## commission_retroactive_topup_cuts

**Columns**
- `commission_retroactive_topup_cuts_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL
- `promoter_id` bigint NOT NULL
- `ledger_type` character varying(40) NOT NULL
- `accrual_period_start` date NOT NULL
- `accrual_period_end` date NOT NULL
- `cut_sequence` integer NOT NULL
- `cut_start` date NOT NULL
- `cut_end` date NOT NULL
- `basis_amount_cumulative` numeric(14,2) NOT NULL
- `target_amount_cumulative` numeric(14,2) NOT NULL
- `already_paid_amount` numeric(14,2) NOT NULL
- `retro_amount` numeric(14,2) NOT NULL
- `currency_id` bigint NOT NULL
- `tier_id` bigint
- `tier_name_snapshot` character varying(80)
- `payout_payment_id` bigint
- `payout_reference` character varying(120)
- `paid_at` timestamp with time zone
- `voided_at` timestamp with time zone
- `void_reason` text
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50) NOT NULL DEFAULT 'PENDING'::character varying
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid

**Constraints**
- PK PRIMARY KEY (commission_retroactive_topup_cuts_id)
- FK `commission_retroactive_topup_cuts_currency_id_fkey` FOREIGN KEY (currency_id) REFERENCES currencies(currencies_id)
- FK `commission_retroactive_topup_cuts_payout_payment_id_fkey` FOREIGN KEY (payout_payment_id) REFERENCES payments(payments_id)
- FK `commission_retroactive_topup_cuts_promoter_id_fkey` FOREIGN KEY (promoter_id) REFERENCES promoters(promoters_id)
- UNIQUE `commission_retroactive_topup_cuts_uuid_key` UNIQUE (uuid)
- UNIQUE `uq_commission_retroactive_topup_cuts_key` UNIQUE (promoter_id, ledger_type, accrual_period_start, accrual_period_end, cut_sequence)
- CHECK `chk_commission_retroactive_topup_cuts_ledger_type` CHECK (((ledger_type)::text = ANY ((ARRAY['DIRECT_INSCRIPTION'::character varying, 'DIRECT_COLLECTION'::character varying, 'HIERARCHY_OVERRIDE_INSCRIPTION'::character varying, 'HIERARCHY_OVERRIDE_COLLECTION'::character varying])::text[])))
- CHECK `chk_commission_retroactive_topup_cuts_sequence` CHECK ((cut_sequence >= 1))
- CHECK `chk_commission_retroactive_topup_cuts_status` CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PAID'::character varying, 'VOIDED'::character varying])::text[])))

**Triggers**
- `trg_commission_retroactive_topup_cuts_updated_at` BEFORE UPDATE ON app.commission_retroactive_topup_cuts FOR EACH ROW EXECUTE FUNCTION set_updated_at()

## commission_retroactive_topups

**Columns**
- `commission_retroactive_topups_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL
- `promoter_id` bigint NOT NULL
- `ledger_type` character varying(40) NOT NULL
- `period_start` date NOT NULL
- `period_end` date NOT NULL
- `basis_amount` numeric(10,2) NOT NULL
- `target_amount` numeric(10,2) NOT NULL
- `already_paid_amount` numeric(10,2) NOT NULL
- `retro_amount` numeric(10,2) NOT NULL
- `currency_id` bigint NOT NULL
- `tier_id` bigint
- `tier_name_snapshot` character varying(80)
- `payout_reference` character varying(120)
- `paid_at` timestamp with time zone
- `voided_at` timestamp with time zone
- `void_reason` text
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50) NOT NULL DEFAULT 'PENDING'::character varying
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid
- `payout_payment_id` bigint

**Constraints**
- PK PRIMARY KEY (commission_retroactive_topups_id)
- FK `commission_retroactive_topups_currency_id_fkey` FOREIGN KEY (currency_id) REFERENCES currencies(currencies_id)
- FK `commission_retroactive_topups_payout_payment_id_fkey` FOREIGN KEY (payout_payment_id) REFERENCES payments(payments_id)
- FK `commission_retroactive_topups_promoter_id_fkey` FOREIGN KEY (promoter_id) REFERENCES promoters(promoters_id)
- UNIQUE `commission_retroactive_topups_uuid_key` UNIQUE (uuid)
- UNIQUE `uq_commission_retroactive_topups_key` UNIQUE (promoter_id, ledger_type, period_start, period_end)
- CHECK `chk_commission_retroactive_topups_ledger_type` CHECK (((ledger_type)::text = ANY ((ARRAY['DIRECT_INSCRIPTION'::character varying, 'DIRECT_COLLECTION'::character varying, 'HIERARCHY_OVERRIDE_INSCRIPTION'::character varying, 'HIERARCHY_OVERRIDE_COLLECTION'::character varying])::text[])))
- CHECK `chk_commission_retroactive_topups_status` CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PAID'::character varying, 'VOIDED'::character varying])::text[])))

**Indexes**
- `idx_commission_retroactive_topups_payout_payment` btree (payout_payment_id)
- `idx_commission_retroactive_topups_promoter_status` btree (promoter_id, status, period_start, period_end)

**Triggers**
- `trg_commission_retroactive_topups_updated_at` BEFORE UPDATE ON app.commission_retroactive_topups FOR EACH ROW EXECUTE FUNCTION set_updated_at()

## commission_tier_promoter_types

**Columns**
- `commission_tier_promoter_types_id` bigint NOT NULL IDENTITY
- `commission_tier_id` bigint NOT NULL
- `promoter_type_id` bigint NOT NULL
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid

**Constraints**
- PK PRIMARY KEY (commission_tier_promoter_types_id)
- FK `commission_tier_promoter_types_commission_tier_id_fkey` FOREIGN KEY (commission_tier_id) REFERENCES commission_tiers(commission_tiers_id) ON DELETE CASCADE
- FK `commission_tier_promoter_types_promoter_type_id_fkey` FOREIGN KEY (promoter_type_id) REFERENCES promoter_types(promoter_types_id)
- UNIQUE `commission_tier_promoter_type_commission_tier_id_promoter_t_key` UNIQUE (commission_tier_id, promoter_type_id)

**Indexes**
- `idx_commission_tier_promoter_types_type` btree (promoter_type_id)

## commission_tiers

**Columns**
- `commission_tiers_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL
- `name` character varying(80) NOT NULL
- `plan_type` character varying(20)
- `threshold_count` integer NOT NULL DEFAULT 0
- `commission_pct` numeric(5,2)
- `flat_amount` numeric(10,2)
- `accrual_period_strategy` character varying(20) NOT NULL
- `applies_to` character varying(20) NOT NULL
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50)
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid
- `partial_settlement_period_strategy` character varying(20) NOT NULL DEFAULT 'MONTHLY'::character varying
- `final_settlement_period_strategy` character varying(20) NOT NULL DEFAULT 'MONTHLY'::character varying
- `campaign_id` bigint
- `starts_at` timestamp with time zone
- `ends_at` timestamp with time zone
- `flat_amount_currency_id` bigint
- `description` text
- `retroactive_settlement_period_strategy` character varying(20) NOT NULL DEFAULT 'MONTHLY'::character varying
- `accrual_period_anchor` smallint
- `partial_settlement_period_anchor` smallint
- `final_settlement_period_anchor` smallint
- `retroactive_settlement_period_anchor` smallint
- `basis` character varying(10) NOT NULL DEFAULT 'COUNT'::character varying
- `threshold_amount` numeric(14,2)
- `threshold_amount_currency_id` bigint

**Constraints**
- PK PRIMARY KEY (commission_tiers_id)
- FK `commission_tiers_campaign_id_fkey` FOREIGN KEY (campaign_id) REFERENCES campaigns(campaigns_id)
- FK `commission_tiers_flat_amount_currency_id_fkey` FOREIGN KEY (flat_amount_currency_id) REFERENCES currencies(currencies_id)
- FK `commission_tiers_threshold_amount_currency_id_fkey` FOREIGN KEY (threshold_amount_currency_id) REFERENCES currencies(currencies_id)
- UNIQUE `commission_tiers_uuid_key` UNIQUE (uuid)
- CHECK `chk_commission_tiers_accrual_period_anchor_range` CHECK (((accrual_period_anchor IS NULL) OR ((accrual_period_anchor >= 1) AND (accrual_period_anchor <= 31))))
- CHECK `chk_commission_tiers_accrual_period_strategy` CHECK (((accrual_period_strategy)::text = ANY ((ARRAY['DAILY'::character varying, 'WEEKLY'::character varying, 'BIWEEKLY'::character varying, 'MONTHLY'::character varying, 'QUARTERLY'::character varying, 'SEMIANNUAL'::character varying, 'ANNUAL'::character varying])::text[])))
- CHECK `chk_commission_tiers_applies_to` CHECK (((applies_to)::text = ANY ((ARRAY['INSCRIPTION'::character varying, 'MONTHLY'::character varying, 'BOTH'::character varying])::text[])))
- CHECK `chk_commission_tiers_basis` CHECK (((basis)::text = ANY ((ARRAY['COUNT'::character varying, 'AMOUNT'::character varying])::text[])))
- CHECK `chk_commission_tiers_basis_field` CHECK (((((basis)::text = 'COUNT'::text) AND (threshold_amount IS NULL) AND (threshold_amount_currency_id IS NULL)) OR (((basis)::text = 'AMOUNT'::text) AND (threshold_amount IS NOT NULL) AND (threshold_amount_currency_id IS NOT NULL))))
- CHECK `chk_commission_tiers_final_settlement_period_anchor_range` CHECK (((final_settlement_period_anchor IS NULL) OR ((final_settlement_period_anchor >= 1) AND (final_settlement_period_anchor <= 31))))
- CHECK `chk_commission_tiers_final_settlement_period_strategy` CHECK (((final_settlement_period_strategy)::text = ANY ((ARRAY['DAILY'::character varying, 'WEEKLY'::character varying, 'BIWEEKLY'::character varying, 'MONTHLY'::character varying, 'QUARTERLY'::character varying, 'SEMIANNUAL'::character varying, 'ANNUAL'::character varying])::text[])))
- CHECK `chk_commission_tiers_flat` CHECK (((flat_amount IS NULL) OR (flat_amount >= (0)::numeric)))
- CHECK `chk_commission_tiers_flat_needs_currency` CHECK (((flat_amount IS NULL) OR (flat_amount_currency_id IS NOT NULL)))
- CHECK `chk_commission_tiers_partial_settlement_period_anchor_range` CHECK (((partial_settlement_period_anchor IS NULL) OR ((partial_settlement_period_anchor >= 1) AND (partial_settlement_period_anchor <= 31))))
- CHECK `chk_commission_tiers_partial_settlement_period_strategy` CHECK (((partial_settlement_period_strategy)::text = ANY ((ARRAY['DAILY'::character varying, 'WEEKLY'::character varying, 'BIWEEKLY'::character varying, 'MONTHLY'::character varying, 'QUARTERLY'::character varying, 'SEMIANNUAL'::character varying, 'ANNUAL'::character varying])::text[])))
- CHECK `chk_commission_tiers_pct` CHECK (((commission_pct IS NULL) OR ((commission_pct >= (0)::numeric) AND (commission_pct <= (100)::numeric))))
- CHECK `chk_commission_tiers_pct_xor_flat` CHECK (((commission_pct IS NOT NULL) <> (flat_amount IS NOT NULL)))
- CHECK `chk_commission_tiers_plan_type` CHECK (((plan_type IS NULL) OR ((plan_type)::text = ANY ((ARRAY['INDIVIDUAL'::character varying, 'FAMILIAR'::character varying, 'CORPORATIVO'::character varying])::text[]))))
- CHECK `chk_commission_tiers_retroactive_settlement_period_anchor_range` CHECK (((retroactive_settlement_period_anchor IS NULL) OR ((retroactive_settlement_period_anchor >= 1) AND (retroactive_settlement_period_anchor <= 31))))
- CHECK `chk_commission_tiers_retroactive_settlement_period_strategy` CHECK (((retroactive_settlement_period_strategy)::text = ANY ((ARRAY['DAILY'::character varying, 'WEEKLY'::character varying, 'BIWEEKLY'::character varying, 'MONTHLY'::character varying, 'QUARTERLY'::character varying, 'SEMIANNUAL'::character varying, 'ANNUAL'::character varying])::text[])))
- CHECK `chk_commission_tiers_threshold` CHECK ((threshold_count >= 0))

**Indexes**
- `idx_commission_tiers_campaign` btree (campaign_id) WHERE (campaign_id IS NOT NULL)
- `idx_commission_tiers_lookup` btree (plan_type, applies_to, threshold_count DESC) WHERE is_active

**Triggers**
- `trg_commission_tiers_updated_at` BEFORE UPDATE ON app.commission_tiers FOR EACH ROW EXECUTE FUNCTION set_updated_at()

**Referenced by** `commission_tier_promoter_types`.(commission_tier_id), `commissions`.(commission_tier_id)

## commissions

**Columns**
- `commissions_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `promoter_id` bigint NOT NULL
- `payment_id` bigint NOT NULL
- `member_id` bigint NOT NULL
- `amount` numeric(10,2) NOT NULL
- `calculation_basis` numeric(10,2) NOT NULL
- `commission_pct` numeric(5,2)
- `flat_amount` numeric(10,2)
- `commission_tier_id` bigint
- `tier_name_snapshot` character varying(80)
- `applies_to` character varying(20) NOT NULL
- `period_strategy` character varying(20) NOT NULL DEFAULT 'MONTHLY'::character varying
- `period_start` date NOT NULL
- `period_end` date NOT NULL
- `earned_at` timestamp with time zone NOT NULL DEFAULT now()
- `payout_reference` character varying(120)
- `paid_at` timestamp with time zone
- `voided_at` timestamp with time zone
- `void_reason` text
- `admin_notes` text
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50) NOT NULL DEFAULT 'PENDING'::character varying
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid
- `collection_days` integer — Days late the recurring payment was collected (payment_date - scheduled collection date), when priced by the collection-commission engine.
- `collection_tier_id` bigint — FK to the collection_commission_tiers bucket applied, when priced by the collection-commission engine.
- `currency_id` bigint NOT NULL
- `approved_by_user_id` bigint
- `approved_at` timestamp with time zone
- `rejection_reason` text
- `exchange_rate_at_earned` numeric(18,8)
- `earned_rate_date` date
- `exchange_rate_at_paid` numeric(18,8)
- `paid_rate_date` date
- `payout_payment_id` bigint
- `auto_approved` boolean NOT NULL DEFAULT false

**Constraints**
- PK PRIMARY KEY (commissions_id)
- FK `commissions_approved_by_user_id_fkey` FOREIGN KEY (approved_by_user_id) REFERENCES users(users_id)
- FK `commissions_collection_tier_id_fkey` FOREIGN KEY (collection_tier_id) REFERENCES collection_commission_tiers(collection_commission_tiers_id)
- FK `commissions_currency_id_fkey` FOREIGN KEY (currency_id) REFERENCES currencies(currencies_id)
- FK `commissions_member_id_fkey` FOREIGN KEY (member_id) REFERENCES members(members_id)
- FK `commissions_payment_id_fkey` FOREIGN KEY (payment_id) REFERENCES payments(payments_id)
- FK `commissions_payout_payment_id_fkey` FOREIGN KEY (payout_payment_id) REFERENCES payments(payments_id)
- FK `commissions_promoter_id_fkey` FOREIGN KEY (promoter_id) REFERENCES promoters(promoters_id)
- FK `fk_commissions_tier` FOREIGN KEY (commission_tier_id) REFERENCES commission_tiers(commission_tiers_id)
- UNIQUE `commissions_uuid_key` UNIQUE (uuid)
- CHECK `chk_commissions_approved_consistency` CHECK ((((status)::text <> 'APPROVED'::text) OR auto_approved OR ((approved_by_user_id IS NOT NULL) AND (approved_at IS NOT NULL))))
- CHECK `chk_commissions_collection_days` CHECK ((collection_days >= 0))
- CHECK `chk_commissions_earned_in_period` CHECK ((((earned_at)::date >= period_start) AND ((earned_at)::date <= period_end)))
- CHECK `chk_commissions_paid_consistency` CHECK ((((status)::text <> 'PAID'::text) OR ((paid_at IS NOT NULL) AND (payout_reference IS NOT NULL))))
- CHECK `chk_commissions_pct_xor_flat` CHECK ((((commission_pct IS NOT NULL) AND (flat_amount IS NULL)) OR ((commission_pct IS NULL) AND (flat_amount IS NOT NULL))))
- CHECK `chk_commissions_period_bounds` CHECK ((period_end >= period_start))
- CHECK `chk_commissions_rejected_consistency` CHECK ((((status)::text <> 'REJECTED'::text) OR ((approved_by_user_id IS NOT NULL) AND (approved_at IS NOT NULL) AND (rejection_reason IS NOT NULL))))
- CHECK `chk_commissions_voided_consistency` CHECK ((((status)::text <> 'VOIDED'::text) OR ((voided_at IS NOT NULL) AND (void_reason IS NOT NULL))))
- CHECK `commissions_amount_check` CHECK ((amount > (0)::numeric))
- CHECK `commissions_applies_to_check` CHECK (((applies_to)::text = ANY ((ARRAY['INSCRIPTION'::character varying, 'MONTHLY'::character varying])::text[])))
- CHECK `commissions_calculation_basis_check` CHECK ((calculation_basis >= (0)::numeric))
- CHECK `commissions_commission_pct_check` CHECK (((commission_pct IS NULL) OR ((commission_pct >= (0)::numeric) AND (commission_pct <= (100)::numeric))))
- CHECK `commissions_flat_amount_check` CHECK (((flat_amount IS NULL) OR (flat_amount >= (0)::numeric)))
- CHECK `commissions_period_strategy_check` CHECK (((period_strategy)::text = ANY ((ARRAY['DAILY'::character varying, 'WEEKLY'::character varying, 'BIWEEKLY'::character varying, 'MONTHLY'::character varying, 'QUARTERLY'::character varying, 'SEMIANNUAL'::character varying, 'ANNUAL'::character varying])::text[])))
- CHECK `commissions_status_check` CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying, 'PAID'::character varying, 'VOIDED'::character varying, 'DISPUTED'::character varying])::text[])))

**Indexes**
- `idx_commissions_active_created` btree (created_at DESC) WHERE is_active
- `idx_commissions_collection_tier` btree (collection_tier_id) WHERE (collection_tier_id IS NOT NULL)
- `idx_commissions_currency` btree (currency_id)
- `idx_commissions_earned_window` btree (earned_at) WHERE (is_active AND ((status)::text = ANY ((ARRAY['APPROVED'::character varying, 'PAID'::character varying])::text[])))
- `idx_commissions_member_earned` btree (member_id, earned_at DESC) WHERE is_active
- `idx_commissions_payment_id` btree (payment_id)
- `idx_commissions_payout_payment` btree (payout_payment_id)
- `idx_commissions_promoter_status_period` btree (promoter_id, status, period_start) WHERE is_active
- `idx_commissions_status_period` btree (status, period_start, period_end) WHERE is_active
- `idx_commissions_tier` btree (commission_tier_id) WHERE (commission_tier_id IS NOT NULL)
- `uniq_commissions_payment_promoter_active` UNIQUE btree (payment_id, promoter_id) WHERE ((status)::text <> 'VOIDED'::text)

**Triggers**
- `trg_commissions_updated_at` BEFORE UPDATE ON app.commissions FOR EACH ROW EXECUTE FUNCTION set_updated_at()

**Referenced by** `promoter_hierarchy_overrides`.(source_commission_id)

## company_bank_account_payment_methods

> Métodos de pago que acepta cada cuenta de la empresa; lo que exige la cuenta es la suma de los is_mandatory_* de sus métodos.

**Columns**
- `company_bank_account_id` bigint NOT NULL
- `payment_method_id` bigint NOT NULL

**Constraints**
- PK PRIMARY KEY (company_bank_account_id, payment_method_id)
- FK `company_bank_account_payment_metho_company_bank_account_id_fkey` FOREIGN KEY (company_bank_account_id) REFERENCES company_bank_accounts(company_bank_accounts_id) ON DELETE CASCADE
- FK `company_bank_account_payment_methods_payment_method_id_fkey` FOREIGN KEY (payment_method_id) REFERENCES payment_methods(payment_methods_id)

**Indexes**
- `idx_company_account_methods_method` btree (payment_method_id)

## company_bank_accounts

> Cuentas bancarias propias de OptiBienestar donde los afiliados depositan; se elige una por línea de pago.

**Columns**
- `company_bank_accounts_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `bank_id` bigint
- `currency_id` bigint NOT NULL
- `account_type` character varying(40)
- `account_number` character varying(40)
- `holder_name` character varying(160) NOT NULL
- `holder_document` character varying(30)
- `notes` character varying(255)
- `sort_order` integer NOT NULL DEFAULT 0
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50)
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid
- `is_public` boolean NOT NULL DEFAULT false — TRUE = visible sin autenticación en GET /v1/public/company-bank-accounts; FALSE = solo para quien registra pagos.
- `phone` character varying(30) — Teléfono de la cuenta (pago móvil).
- `email` character varying(160) — Correo de la cuenta (Zelle y similares).

**Constraints**
- PK PRIMARY KEY (company_bank_accounts_id)
- FK `company_bank_accounts_bank_id_fkey` FOREIGN KEY (bank_id) REFERENCES banks(banks_id)
- FK `company_bank_accounts_currency_id_fkey` FOREIGN KEY (currency_id) REFERENCES currencies(currencies_id)
- UNIQUE `company_bank_accounts_uuid_key` UNIQUE (uuid)

**Indexes**
- `uq_company_bank_accounts_number` UNIQUE btree (bank_id, account_number) WHERE is_active

**Triggers**
- `trg_company_bank_accounts_updated_at` BEFORE UPDATE ON app.company_bank_accounts FOR EACH ROW EXECUTE FUNCTION set_updated_at()

**Referenced by** `company_bank_account_payment_methods`.(company_bank_account_id), `payment_lines`.(company_bank_account_id)

## competitive_commission_award_settlements

**Columns**
- `competitive_commission_award_settlements_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `competitive_commission_rule_id` bigint NOT NULL
- `competitive_commission_award_id` bigint
- `promoter_id` bigint NOT NULL
- `period_start` date NOT NULL
- `period_end` date NOT NULL
- `cut_kind` character varying(20) NOT NULL
- `cut_sequence` integer NOT NULL
- `cut_start` date NOT NULL
- `cut_end` date NOT NULL
- `award_position_at_cut` integer
- `basis_amount_cumulative` numeric(14,2)
- `entitlement_cumulative` numeric(12,2) NOT NULL
- `already_paid_amount` numeric(12,2) NOT NULL
- `amount` numeric(12,2) NOT NULL
- `currency_id` bigint NOT NULL
- `exchange_rate_at_paid` numeric(18,8)
- `paid_rate_date` date
- `payout_reference` character varying(120)
- `payout_payment_id` bigint
- `paid_at` timestamp with time zone
- `voided_at` timestamp with time zone
- `void_reason` text
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50) NOT NULL DEFAULT 'PENDING'::character varying
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid

**Constraints**
- PK PRIMARY KEY (competitive_commission_award_settlements_id)
- FK `competitive_commission_award__competitive_commission_award_fkey` FOREIGN KEY (competitive_commission_award_id) REFERENCES competitive_commission_awards(competitive_commission_awards_id)
- FK `competitive_commission_award__competitive_commission_rule__fkey` FOREIGN KEY (competitive_commission_rule_id) REFERENCES competitive_commission_rules(competitive_commission_rules_id)
- FK `competitive_commission_award_settlements_currency_id_fkey` FOREIGN KEY (currency_id) REFERENCES currencies(currencies_id)
- FK `competitive_commission_award_settlements_payout_payment_id_fkey` FOREIGN KEY (payout_payment_id) REFERENCES payments(payments_id)
- FK `competitive_commission_award_settlements_promoter_id_fkey` FOREIGN KEY (promoter_id) REFERENCES promoters(promoters_id)
- UNIQUE `competitive_commission_award_settlements_uuid_key` UNIQUE (uuid)
- UNIQUE `uq_ccas_cut` UNIQUE (competitive_commission_rule_id, promoter_id, period_start, cut_kind, cut_sequence)
- CHECK `competitive_commission_award_settlements_amount_check` CHECK ((amount > (0)::numeric))
- CHECK `competitive_commission_award_settlements_cut_kind_check` CHECK (((cut_kind)::text = ANY ((ARRAY['PARTIAL'::character varying, 'RETROACTIVE'::character varying, 'FINAL'::character varying])::text[])))
- CHECK `competitive_commission_award_settlements_cut_sequence_check` CHECK ((cut_sequence >= 1))
- CHECK `competitive_commission_award_settlements_status_check` CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PAID'::character varying, 'VOIDED'::character varying])::text[])))

**Indexes**
- `idx_ccas_award` btree (competitive_commission_award_id)
- `idx_ccas_pending` btree (status) WHERE ((status)::text = 'PENDING'::text)

**Triggers**
- `trg_competitive_commission_award_settlements_updated_at` BEFORE UPDATE ON app.competitive_commission_award_settlements FOR EACH ROW EXECUTE FUNCTION set_updated_at()

## competitive_commission_awards

**Columns**
- `competitive_commission_awards_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `competitive_commission_rule_id` bigint NOT NULL
- `competitive_commission_rule_position_id` bigint NOT NULL
- `promoter_id` bigint NOT NULL
- `period_start` date NOT NULL
- `period_end` date NOT NULL
- `award_position` integer NOT NULL
- `tie_group_size` smallint NOT NULL DEFAULT 1
- `metric_value` numeric(14,2) NOT NULL
- `metric_transaction_count` integer NOT NULL DEFAULT 0
- `achieved_at` timestamp with time zone
- `awarded_at` timestamp with time zone NOT NULL DEFAULT now()
- `confirmed_at` timestamp with time zone
- `reward_type` character varying(20) NOT NULL
- `flat_amount` numeric(10,2)
- `reward_pct` numeric(5,2)
- `basis_amount` numeric(14,2)
- `amount` numeric(12,2) NOT NULL
- `currency_id` bigint NOT NULL
- `exchange_rate_at_award` numeric(18,8)
- `award_rate_date` date
- `paid_at` timestamp with time zone
- `payout_reference` character varying(120)
- `payout_payment_id` bigint
- `exchange_rate_at_paid` numeric(18,8)
- `paid_rate_date` date
- `voided_at` timestamp with time zone
- `void_reason` text
- `admin_notes` text
- `rule_name_snapshot` character varying(150) NOT NULL
- `snapshot_json` jsonb NOT NULL
- `selection_source` character varying(10) NOT NULL DEFAULT 'AUTO'::character varying
- `manual_decision_id` bigint
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50) NOT NULL DEFAULT 'PROVISIONAL'::character varying
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid

**Constraints**
- PK PRIMARY KEY (competitive_commission_awards_id)
- FK `competitive_commission_award_competitive_commission_rule__fkey1` FOREIGN KEY (competitive_commission_rule_position_id) REFERENCES competitive_commission_rule_positions(competitive_commission_rule_positions_id)
- FK `competitive_commission_awards_competitive_commission_rule__fkey` FOREIGN KEY (competitive_commission_rule_id) REFERENCES competitive_commission_rules(competitive_commission_rules_id)
- FK `competitive_commission_awards_currency_id_fkey` FOREIGN KEY (currency_id) REFERENCES currencies(currencies_id)
- FK `competitive_commission_awards_payout_payment_id_fkey` FOREIGN KEY (payout_payment_id) REFERENCES payments(payments_id)
- FK `competitive_commission_awards_promoter_id_fkey` FOREIGN KEY (promoter_id) REFERENCES promoters(promoters_id)
- FK `fk_cca_manual_decision` FOREIGN KEY (manual_decision_id) REFERENCES competitive_commission_manual_decisions(competitive_commission_manual_decisions_id)
- UNIQUE `competitive_commission_awards_uuid_key` UNIQUE (uuid)
- CHECK `chk_cca_paid` CHECK ((((status)::text <> 'PAID'::text) OR (paid_at IS NOT NULL)))
- CHECK `chk_cca_period` CHECK ((period_end >= period_start))
- CHECK `chk_cca_voided` CHECK ((((status)::text <> 'VOIDED'::text) OR (voided_at IS NOT NULL)))
- CHECK `competitive_commission_awards_amount_check` CHECK ((amount >= (0)::numeric))
- CHECK `competitive_commission_awards_award_position_check` CHECK ((award_position >= 1))
- CHECK `competitive_commission_awards_selection_source_check` CHECK (((selection_source)::text = ANY ((ARRAY['AUTO'::character varying, 'MANUAL'::character varying])::text[])))
- CHECK `competitive_commission_awards_status_check` CHECK (((status)::text = ANY ((ARRAY['PROVISIONAL'::character varying, 'PENDING'::character varying, 'PAID'::character varying, 'VOIDED'::character varying])::text[])))
- CHECK `competitive_commission_awards_tie_group_size_check` CHECK ((tie_group_size >= 1))

**Indexes**
- `idx_cca_promoter` btree (promoter_id, created_at DESC)
- `idx_cca_status` btree (status) WHERE ((status)::text = ANY ((ARRAY['PROVISIONAL'::character varying, 'PENDING'::character varying])::text[]))
- `uq_cca_rule_period_position` UNIQUE btree (competitive_commission_rule_id, period_start, award_position) WHERE (is_active AND ((status)::text <> 'VOIDED'::text) AND (tie_group_size = 1))
- `uq_cca_rule_period_promoter` UNIQUE btree (competitive_commission_rule_id, period_start, promoter_id) WHERE (is_active AND ((status)::text <> 'VOIDED'::text))

**Triggers**
- `trg_competitive_commission_awards_updated_at` BEFORE UPDATE ON app.competitive_commission_awards FOR EACH ROW EXECUTE FUNCTION set_updated_at()

**Referenced by** `competitive_commission_award_settlements`.(competitive_commission_award_id)

## competitive_commission_manual_decisions

**Columns**
- `competitive_commission_manual_decisions_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `competitive_commission_rule_id` bigint NOT NULL
- `competitive_commission_tie_id` bigint
- `period_start` date NOT NULL
- `kind` character varying(20) NOT NULL
- `award_position` integer
- `promoter_id` bigint NOT NULL
- `replaced_promoter_id` bigint
- `exclude_from_group` boolean NOT NULL DEFAULT false
- `reason_category` character varying(30) NOT NULL DEFAULT 'OTHER'::character varying
- `reason` text NOT NULL
- `decided_by` uuid NOT NULL
- `decided_at` timestamp with time zone NOT NULL DEFAULT now()
- `reverted_at` timestamp with time zone
- `reverted_by` uuid
- `revert_reason` text
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50) NOT NULL DEFAULT 'ACTIVE'::character varying
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid

**Constraints**
- PK PRIMARY KEY (competitive_commission_manual_decisions_id)
- FK `competitive_commission_manual_competitive_commission_rule__fkey` FOREIGN KEY (competitive_commission_rule_id) REFERENCES competitive_commission_rules(competitive_commission_rules_id)
- FK `competitive_commission_manual_competitive_commission_tie_i_fkey` FOREIGN KEY (competitive_commission_tie_id) REFERENCES competitive_commission_ties(competitive_commission_ties_id)
- FK `competitive_commission_manual_decisio_replaced_promoter_id_fkey` FOREIGN KEY (replaced_promoter_id) REFERENCES promoters(promoters_id)
- FK `competitive_commission_manual_decisions_promoter_id_fkey` FOREIGN KEY (promoter_id) REFERENCES promoters(promoters_id)
- UNIQUE `competitive_commission_manual_decisions_uuid_key` UNIQUE (uuid)
- CHECK `chk_ccmd_redirect` CHECK ((((kind)::text <> 'REDIRECT'::text) OR ((replaced_promoter_id IS NOT NULL) AND (award_position IS NOT NULL))))
- CHECK `chk_ccmd_revert` CHECK ((((status)::text <> 'REVERTED'::text) OR ((reverted_at IS NOT NULL) AND (length(TRIM(BOTH FROM revert_reason)) >= 10))))
- CHECK `competitive_commission_manual_decisions_award_position_check` CHECK ((award_position >= 1))
- CHECK `competitive_commission_manual_decisions_kind_check` CHECK (((kind)::text = ANY ((ARRAY['TIE_RESOLUTION'::character varying, 'REDIRECT'::character varying, 'DISQUALIFY'::character varying])::text[])))
- CHECK `competitive_commission_manual_decisions_reason_category_check` CHECK (((reason_category)::text = ANY ((ARRAY['TIE_BREAK'::character varying, 'UNSPORTSMANLIKE_CONDUCT'::character varying, 'DATA_ERROR'::character varying, 'POLICY'::character varying, 'OTHER'::character varying])::text[])))
- CHECK `competitive_commission_manual_decisions_reason_check` CHECK ((length(TRIM(BOTH FROM reason)) >= 10))
- CHECK `competitive_commission_manual_decisions_status_check` CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'STALE'::character varying, 'REVERTED'::character varying])::text[])))

**Indexes**
- `idx_ccmd_rule_period` btree (competitive_commission_rule_id, period_start) WHERE (is_active AND ((status)::text = 'ACTIVE'::text))

**Triggers**
- `trg_competitive_commission_manual_decisions_updated_at` BEFORE UPDATE ON app.competitive_commission_manual_decisions FOR EACH ROW EXECUTE FUNCTION set_updated_at()

**Referenced by** `competitive_commission_awards`.(manual_decision_id)

## competitive_commission_rule_positions

**Columns**
- `competitive_commission_rule_positions_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `competitive_commission_rule_id` bigint NOT NULL
- `position_from` integer NOT NULL
- `position_to` integer NOT NULL
- `label` character varying(80)
- `reward_type` character varying(20) NOT NULL
- `flat_amount` numeric(10,2)
- `reward_pct` numeric(5,2)
- `reward_currency_id` bigint NOT NULL
- `reward_min_amount` numeric(10,2)
- `reward_max_amount` numeric(10,2)
- `min_threshold_count` integer
- `min_threshold_amount` numeric(14,2)
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50) NOT NULL DEFAULT 'ACTIVE'::character varying
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid

**Constraints**
- PK PRIMARY KEY (competitive_commission_rule_positions_id)
- FK `competitive_commission_rule_p_competitive_commission_rule__fkey` FOREIGN KEY (competitive_commission_rule_id) REFERENCES competitive_commission_rules(competitive_commission_rules_id) ON DELETE CASCADE
- FK `competitive_commission_rule_positions_reward_currency_id_fkey` FOREIGN KEY (reward_currency_id) REFERENCES currencies(currencies_id)
- UNIQUE `competitive_commission_rule_positions_uuid_key` UNIQUE (uuid)
- CHECK `chk_ccrp_minmax` CHECK (((reward_min_amount IS NULL) OR (reward_max_amount IS NULL) OR (reward_max_amount >= reward_min_amount)))
- CHECK `chk_ccrp_range` CHECK ((position_to >= position_from))
- CHECK `chk_ccrp_reward` CHECK (((((reward_type)::text = 'FLAT'::text) AND (flat_amount IS NOT NULL) AND (reward_pct IS NULL)) OR (((reward_type)::text = 'PERCENTAGE'::text) AND (reward_pct IS NOT NULL) AND (flat_amount IS NULL))))
- CHECK `competitive_commission_rule_position_min_threshold_amount_check` CHECK ((min_threshold_amount > (0)::numeric))
- CHECK `competitive_commission_rule_positions_flat_amount_check` CHECK ((flat_amount > (0)::numeric))
- CHECK `competitive_commission_rule_positions_min_threshold_count_check` CHECK ((min_threshold_count > 0))
- CHECK `competitive_commission_rule_positions_position_from_check` CHECK ((position_from >= 1))
- CHECK `competitive_commission_rule_positions_reward_max_amount_check` CHECK ((reward_max_amount > (0)::numeric))
- CHECK `competitive_commission_rule_positions_reward_min_amount_check` CHECK ((reward_min_amount >= (0)::numeric))
- CHECK `competitive_commission_rule_positions_reward_pct_check` CHECK (((reward_pct > (0)::numeric) AND (reward_pct <= (100)::numeric)))
- CHECK `competitive_commission_rule_positions_reward_type_check` CHECK (((reward_type)::text = ANY ((ARRAY['FLAT'::character varying, 'PERCENTAGE'::character varying])::text[])))

**Indexes**
- `uq_ccrp_rule_from` UNIQUE btree (competitive_commission_rule_id, position_from)

**Triggers**
- `trg_competitive_commission_rule_positions_updated_at` BEFORE UPDATE ON app.competitive_commission_rule_positions FOR EACH ROW EXECUTE FUNCTION set_updated_at()

**Referenced by** `competitive_commission_awards`.(competitive_commission_rule_position_id)

## competitive_commission_rule_promoter_types

**Columns**
- `competitive_commission_rule_id` bigint NOT NULL
- `promoter_type_id` bigint NOT NULL

**Constraints**
- PK PRIMARY KEY (competitive_commission_rule_id, promoter_type_id)
- FK `competitive_commission_rule__competitive_commission_rule__fkey1` FOREIGN KEY (competitive_commission_rule_id) REFERENCES competitive_commission_rules(competitive_commission_rules_id) ON DELETE CASCADE
- FK `competitive_commission_rule_promoter_type_promoter_type_id_fkey` FOREIGN KEY (promoter_type_id) REFERENCES promoter_types(promoter_types_id)

## competitive_commission_rule_ranks

**Columns**
- `competitive_commission_rule_id` bigint NOT NULL
- `rank_id` bigint NOT NULL

**Constraints**
- PK PRIMARY KEY (competitive_commission_rule_id, rank_id)
- FK `competitive_commission_rule_r_competitive_commission_rule__fkey` FOREIGN KEY (competitive_commission_rule_id) REFERENCES competitive_commission_rules(competitive_commission_rules_id) ON DELETE CASCADE
- FK `competitive_commission_rule_ranks_rank_id_fkey` FOREIGN KEY (rank_id) REFERENCES promoter_ranks(promoter_ranks_id)

## competitive_commission_rules

**Columns**
- `competitive_commission_rules_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `name` character varying(150) NOT NULL
- `description` text
- `metric` character varying(40) NOT NULL
- `competition_type` character varying(20) NOT NULL
- `threshold_count` integer
- `threshold_amount` numeric(14,2)
- `threshold_currency_id` bigint
- `achievement_date_basis` character varying(20) NOT NULL DEFAULT 'APPROVED_AT'::character varying
- `tie_policy` character varying(20) NOT NULL DEFAULT 'MANUAL'::character varying
- `competition_group` character varying(60)
- `group_priority` smallint
- `accrual_period_strategy` character varying(20) NOT NULL
- `partial_settlement_period_strategy` character varying(20) NOT NULL
- `final_settlement_period_strategy` character varying(20) NOT NULL
- `retroactive_settlement_period_strategy` character varying(20) NOT NULL
- `accrual_period_anchor` smallint
- `partial_settlement_period_anchor` smallint
- `final_settlement_period_anchor` smallint
- `retroactive_settlement_period_anchor` smallint
- `confirmation_delay_days` smallint NOT NULL DEFAULT 0
- `campaign_id` bigint
- `starts_at` timestamp with time zone
- `ends_at` timestamp with time zone
- `include_system_promoters` boolean NOT NULL DEFAULT false
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50) NOT NULL DEFAULT 'ACTIVE'::character varying
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid

**Constraints**
- PK PRIMARY KEY (competitive_commission_rules_id)
- FK `competitive_commission_rules_campaign_id_fkey` FOREIGN KEY (campaign_id) REFERENCES campaigns(campaigns_id)
- FK `competitive_commission_rules_threshold_currency_id_fkey` FOREIGN KEY (threshold_currency_id) REFERENCES currencies(currencies_id)
- UNIQUE `competitive_commission_rules_uuid_key` UNIQUE (uuid)
- CHECK `chk_ccr_amount_currency` CHECK (((threshold_amount IS NULL) OR (threshold_currency_id IS NOT NULL)))
- CHECK `chk_ccr_dates_order` CHECK (((starts_at IS NULL) OR (ends_at IS NULL) OR (ends_at > starts_at)))
- CHECK `chk_ccr_end_date_accrual` CHECK ((((accrual_period_strategy)::text <> 'END_DATE'::text) OR ((starts_at IS NOT NULL) AND (ends_at IS NOT NULL))))
- CHECK `chk_ccr_end_date_settlement` CHECK (((ends_at IS NOT NULL) OR ((('END_DATE'::text <> (partial_settlement_period_strategy)::text) AND ('END_DATE'::text <> (final_settlement_period_strategy)::text)) AND ('END_DATE'::text <> (retroactive_settlement_period_strategy)::text))))
- CHECK `chk_ccr_ftr_threshold` CHECK ((((competition_type)::text <> 'FIRST_TO_REACH'::text) OR (threshold_count IS NOT NULL) OR (threshold_amount IS NOT NULL)))
- CHECK `chk_ccr_settlement_axes` CHECK ((((partial_settlement_period_strategy)::text = ANY ((ARRAY['DAILY'::character varying, 'WEEKLY'::character varying, 'BIWEEKLY'::character varying, 'MONTHLY'::character varying, 'QUARTERLY'::character varying, 'SEMIANNUAL'::character varying, 'ANNUAL'::character varying, 'END_DATE'::character varying])::text[])) AND ((final_settlement_period_strategy)::text = ANY ((ARRAY['DAILY'::character varying, 'WEEKLY'::character varying, 'BIWEEKLY'::character varying, 'MONTHLY'::character varying, 'QUARTERLY'::character varying, 'SEMIANNUAL'::character varying, 'ANNUAL'::character varying, 'END_DATE'::character varying])::text[])) AND ((retroactive_settlement_period_strategy)::text = ANY ((ARRAY['DAILY'::character varying, 'WEEKLY'::character varying, 'BIWEEKLY'::character varying, 'MONTHLY'::character varying, 'QUARTERLY'::character varying, 'SEMIANNUAL'::character varying, 'ANNUAL'::character varying, 'END_DATE'::character varying])::text[]))))
- CHECK `chk_ccr_threshold_xor` CHECK ((NOT ((threshold_count IS NOT NULL) AND (threshold_amount IS NOT NULL))))
- CHECK `competitive_commission_rules_accrual_period_anchor_check` CHECK (((accrual_period_anchor >= 1) AND (accrual_period_anchor <= 31)))
- CHECK `competitive_commission_rules_accrual_period_strategy_check` CHECK (((accrual_period_strategy)::text = ANY ((ARRAY['DAILY'::character varying, 'WEEKLY'::character varying, 'BIWEEKLY'::character varying, 'MONTHLY'::character varying, 'QUARTERLY'::character varying, 'SEMIANNUAL'::character varying, 'ANNUAL'::character varying, 'END_DATE'::character varying])::text[])))
- CHECK `competitive_commission_rules_achievement_date_basis_check` CHECK (((achievement_date_basis)::text = ANY ((ARRAY['PAYMENT_DATE'::character varying, 'REGISTERED_AT'::character varying, 'APPROVED_AT'::character varying])::text[])))
- CHECK `competitive_commission_rules_competition_type_check` CHECK (((competition_type)::text = ANY ((ARRAY['FIRST_TO_REACH'::character varying, 'RANKING'::character varying])::text[])))
- CHECK `competitive_commission_rules_confirmation_delay_days_check` CHECK (((confirmation_delay_days >= 0) AND (confirmation_delay_days <= 60)))
- CHECK `competitive_commission_rules_final_settlement_period_anch_check` CHECK (((final_settlement_period_anchor >= 1) AND (final_settlement_period_anchor <= 31)))
- CHECK `competitive_commission_rules_group_priority_check` CHECK ((group_priority >= 1))
- CHECK `competitive_commission_rules_metric_check` CHECK (((metric)::text = ANY ((ARRAY['NEW_SUBSCRIBERS'::character varying, 'ACTIVE_SUBSCRIBERS'::character varying, 'SALES_COUNT'::character varying, 'SALES_AMOUNT'::character varying, 'COLLECTION_COUNT'::character varying, 'COLLECTION_AMOUNT'::character varying, 'ADVANCE_COUNT'::character varying, 'ADVANCE_AMOUNT'::character varying, 'COMMISSION_EARNED'::character varying, 'OVERDUE_SETTLED_COUNT'::character varying, 'OVERDUE_SETTLED_AMOUNT'::character varying])::text[])))
- CHECK `competitive_commission_rules_partial_settlement_period_an_check` CHECK (((partial_settlement_period_anchor >= 1) AND (partial_settlement_period_anchor <= 31)))
- CHECK `competitive_commission_rules_retroactive_settlement_perio_check` CHECK (((retroactive_settlement_period_anchor >= 1) AND (retroactive_settlement_period_anchor <= 31)))
- CHECK `competitive_commission_rules_threshold_amount_check` CHECK ((threshold_amount > (0)::numeric))
- CHECK `competitive_commission_rules_threshold_count_check` CHECK ((threshold_count > 0))
- CHECK `competitive_commission_rules_tie_policy_check` CHECK (((tie_policy)::text = ANY ((ARRAY['STRICT'::character varying, 'SHARED_FULL'::character varying, 'SHARED_SPLIT'::character varying, 'MANUAL'::character varying])::text[])))

**Indexes**
- `idx_ccr_active_eval` btree (accrual_period_strategy) WHERE is_active
- `idx_ccr_campaign` btree (campaign_id) WHERE (campaign_id IS NOT NULL)
- `uq_ccr_group_priority` UNIQUE btree (competition_group, group_priority) WHERE (is_active AND (competition_group IS NOT NULL))

**Triggers**
- `trg_competitive_commission_rules_updated_at` BEFORE UPDATE ON app.competitive_commission_rules FOR EACH ROW EXECUTE FUNCTION set_updated_at()

**Referenced by** `competitive_commission_award_settlements`.(competitive_commission_rule_id), `competitive_commission_awards`.(competitive_commission_rule_id), `competitive_commission_manual_decisions`.(competitive_commission_rule_id), `competitive_commission_rule_positions`.(competitive_commission_rule_id), `competitive_commission_rule_promoter_types`.(competitive_commission_rule_id), `competitive_commission_rule_ranks`.(competitive_commission_rule_id), `competitive_commission_ties`.(competitive_commission_rule_id)

## competitive_commission_tie_candidates

**Columns**
- `competitive_commission_tie_id` bigint NOT NULL
- `promoter_id` bigint NOT NULL
- `metric_value` numeric(14,2) NOT NULL
- `achieved_at` timestamp with time zone
- `metric_transaction_count` integer NOT NULL
- `selected` boolean NOT NULL DEFAULT false

**Constraints**
- PK PRIMARY KEY (competitive_commission_tie_id, promoter_id)
- FK `competitive_commission_tie_ca_competitive_commission_tie_i_fkey` FOREIGN KEY (competitive_commission_tie_id) REFERENCES competitive_commission_ties(competitive_commission_ties_id) ON DELETE CASCADE
- FK `competitive_commission_tie_candidates_promoter_id_fkey` FOREIGN KEY (promoter_id) REFERENCES promoters(promoters_id)

## competitive_commission_ties

**Columns**
- `competitive_commission_ties_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `competitive_commission_rule_id` bigint NOT NULL
- `period_start` date NOT NULL
- `period_end` date NOT NULL
- `position_from` integer NOT NULL
- `slots` integer NOT NULL
- `resolved_by` uuid
- `resolved_at` timestamp with time zone
- `reason` text
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50) NOT NULL DEFAULT 'OPEN'::character varying
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid

**Constraints**
- PK PRIMARY KEY (competitive_commission_ties_id)
- FK `competitive_commission_ties_competitive_commission_rule_id_fkey` FOREIGN KEY (competitive_commission_rule_id) REFERENCES competitive_commission_rules(competitive_commission_rules_id)
- UNIQUE `competitive_commission_ties_uuid_key` UNIQUE (uuid)
- CHECK `chk_cct_resolved` CHECK ((((status)::text <> 'RESOLVED'::text) OR ((resolved_at IS NOT NULL) AND (length(TRIM(BOTH FROM reason)) >= 10))))
- CHECK `competitive_commission_ties_position_from_check` CHECK ((position_from >= 1))
- CHECK `competitive_commission_ties_slots_check` CHECK ((slots >= 1))
- CHECK `competitive_commission_ties_status_check` CHECK (((status)::text = ANY ((ARRAY['OPEN'::character varying, 'RESOLVED'::character varying, 'STALE'::character varying])::text[])))

**Indexes**
- `uq_cct_open` UNIQUE btree (competitive_commission_rule_id, period_start, position_from) WHERE (is_active AND ((status)::text = ANY ((ARRAY['OPEN'::character varying, 'STALE'::character varying])::text[])))

**Triggers**
- `trg_competitive_commission_ties_updated_at` BEFORE UPDATE ON app.competitive_commission_ties FOR EACH ROW EXECUTE FUNCTION set_updated_at()

**Referenced by** `competitive_commission_manual_decisions`.(competitive_commission_tie_id), `competitive_commission_tie_candidates`.(competitive_commission_tie_id)

## contact_messages

**Columns**
- `contact_messages_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `name` character varying(150) NOT NULL
- `email` character varying(254) NOT NULL
- `phone` character varying(30)
- `subject` character varying(200) NOT NULL
- `message` text NOT NULL
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50) NOT NULL DEFAULT 'new'::character varying
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid

**Constraints**
- PK PRIMARY KEY (contact_messages_id)
- UNIQUE `contact_messages_uuid_key` UNIQUE (uuid)
- CHECK `contact_messages_status_check` CHECK (((status)::text = ANY ((ARRAY['new'::character varying, 'read'::character varying, 'archived'::character varying])::text[])))

**Indexes**
- `idx_contact_messages_is_active` btree (is_active) WHERE is_active
- `idx_contact_messages_status` btree (status)

**Triggers**
- `trg_contact_messages_updated_at` BEFORE UPDATE ON app.contact_messages FOR EACH ROW EXECUTE FUNCTION set_updated_at()

## corporate_contracts

**Columns**
- `corporate_contracts_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL
- `plan_id` bigint NOT NULL
- `institution_name` character varying(200) NOT NULL
- `institution_tax_id` character varying(20) NOT NULL
- `contact_user_id` bigint
- `payer_mode` character varying(20) NOT NULL
- `expected_member_count` integer
- `actual_member_count` integer NOT NULL DEFAULT 0
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50)
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid

**Constraints**
- PK PRIMARY KEY (corporate_contracts_id)
- FK `corporate_contracts_contact_user_id_fkey` FOREIGN KEY (contact_user_id) REFERENCES users(users_id)
- FK `corporate_contracts_plan_id_fkey` FOREIGN KEY (plan_id) REFERENCES plans(plans_id)
- UNIQUE `corporate_contracts_uuid_key` UNIQUE (uuid)
- CHECK `chk_corporate_contracts_actual_count` CHECK ((actual_member_count >= 0))
- CHECK `chk_corporate_contracts_payer_mode` CHECK (((payer_mode)::text = ANY ((ARRAY['INSTITUTION_BULK'::character varying, 'INDIVIDUAL_PAYER'::character varying])::text[])))
- CHECK `corporate_contracts_expected_member_count_check` CHECK (((expected_member_count IS NULL) OR (expected_member_count >= 0)))

**Indexes**
- `idx_corporate_contracts_active` btree (created_at DESC) WHERE is_active
- `idx_corporate_contracts_plan` btree (plan_id)

**Triggers**
- `trg_corporate_contracts_updated_at` BEFORE UPDATE ON app.corporate_contracts FOR EACH ROW EXECUTE FUNCTION set_updated_at()

**Referenced by** `members`.(corporate_contract_id), `payments`.(corporate_contract_id)

## countries

**Columns**
- `countries_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `iso_code` character varying(2) NOT NULL
- `name` character varying(100) NOT NULL
- `locale` character varying(10)
- `is_active` boolean NOT NULL DEFAULT true
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid
- `official_currency_id` bigint

**Constraints**
- PK PRIMARY KEY (countries_id)
- FK `countries_official_currency_id_fkey` FOREIGN KEY (official_currency_id) REFERENCES currencies(currencies_id)
- UNIQUE `countries_iso_code_key` UNIQUE (iso_code)
- UNIQUE `countries_name_key` UNIQUE (name)
- UNIQUE `countries_uuid_key` UNIQUE (uuid)

**Indexes**
- `idx_countries_is_active` btree (is_active) WHERE is_active

**Triggers**
- `trg_countries_updated_at` BEFORE UPDATE ON app.countries FOR EACH ROW EXECUTE FUNCTION set_updated_at()

**Referenced by** `holidays`.(country_id), `states`.(country_id)

## currencies

**Columns**
- `currencies_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `code` character varying(4) NOT NULL
- `name` character varying(60) NOT NULL
- `symbol` character varying(6) NOT NULL
- `decimal_places` smallint NOT NULL DEFAULT 2
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50) NOT NULL DEFAULT 'ACTIVE'::character varying
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid

**Constraints**
- PK PRIMARY KEY (currencies_id)
- UNIQUE `currencies_code_key` UNIQUE (code)
- UNIQUE `currencies_uuid_key` UNIQUE (uuid)
- CHECK `currencies_decimal_places_check` CHECK ((decimal_places >= 0))

**Indexes**
- `idx_currencies_is_active` btree (is_active) WHERE is_active

**Triggers**
- `trg_currencies_updated_at` BEFORE UPDATE ON app.currencies FOR EACH ROW EXECUTE FUNCTION set_updated_at()

**Referenced by** `ally_products`.(price_currency_id), `benefit_usages`.(consumption_currency_id), `benefit_usages`.(converted_currency_id), `benefit_usages`.(copay_currency_id), `campaigns`.(target_amount_currency_id), `collection_commission_tiers`.(flat_amount_currency_id), `collection_commission_tiers`.(min_amount_currency_id), `commission_bonus_rules`.(reward_currency_id), `commission_bonus_rules`.(threshold_currency_id), `commission_retroactive_topup_cuts`.(currency_id), `commission_retroactive_topups`.(currency_id), `commission_tiers`.(flat_amount_currency_id), `commission_tiers`.(threshold_amount_currency_id), `commissions`.(currency_id), `company_bank_accounts`.(currency_id), `competitive_commission_award_settlements`.(currency_id), `competitive_commission_awards`.(currency_id), `competitive_commission_rule_positions`.(reward_currency_id), `competitive_commission_rules`.(threshold_currency_id), `countries`.(official_currency_id), `exchange_rates`.(base_currency_id), `exchange_rates`.(quote_currency_id), `hierarchy_override_tiers`.(flat_amount_currency_id), `hierarchy_override_tiers`.(threshold_amount_currency_id), `membership_charges`.(currency_id), `memberships`.(currency_id), `organizations`.(official_currency_id), `organizations`.(reference_currency_id), `payment_lines`.(currency_id), `payments`.(currency_id), `person_credit_entries`.(converted_currency_id), `person_credit_entries`.(currency_id), `plans`.(currency_id), `promoter_bonus_awards`.(reward_currency_id), `promoter_hierarchy_overrides`.(currency_id), `promoter_member_contacts`.(promised_currency_id), `promotions`.(discount_currency_id), `referrals`.(reward_currency_id)

## data_change_audit_log

**Columns**
- `data_change_audit_log_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `entity_key` character varying(80) NOT NULL
- `entity_id` bigint
- `entity_uuid` uuid
- `action` character varying(20) NOT NULL
- `before_json` jsonb
- `after_json` jsonb
- `actor_id` bigint
- `login_audit_log_id` bigint
- `request_method` character varying(10)
- `request_path` character varying(255)
- `restored_from_id` bigint
- `occurred_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_at` timestamp with time zone NOT NULL DEFAULT now()

**Constraints**
- PK PRIMARY KEY (data_change_audit_log_id)
- FK `data_change_audit_log_actor_id_fkey` FOREIGN KEY (actor_id) REFERENCES users(users_id)
- FK `data_change_audit_log_login_audit_log_id_fkey` FOREIGN KEY (login_audit_log_id) REFERENCES login_audit_log(login_audit_log_id)
- FK `data_change_audit_log_restored_from_id_fkey` FOREIGN KEY (restored_from_id) REFERENCES data_change_audit_log(data_change_audit_log_id)
- UNIQUE `data_change_audit_log_uuid_key` UNIQUE (uuid)
- CHECK `chk_data_change_action` CHECK (((action)::text = ANY ((ARRAY['CREATE'::character varying, 'UPDATE'::character varying, 'DELETE'::character varying])::text[])))

**Indexes**
- `idx_data_change_actor_occurred` btree (actor_id, occurred_at DESC)
- `idx_data_change_entity_id` btree (entity_key, entity_id) WHERE (entity_id IS NOT NULL)
- `idx_data_change_entity_occurred` btree (entity_key, entity_uuid, occurred_at DESC)
- `idx_data_change_login_audit` btree (login_audit_log_id) WHERE (login_audit_log_id IS NOT NULL)
- `idx_data_change_occurred_at` btree (occurred_at DESC)

**Referenced by** `data_change_audit_log`.(restored_from_id)

## document_types

**Columns**
- `document_types_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `code` character varying(3) NOT NULL
- `name` character varying(60) NOT NULL
- `description` character varying(200)
- `is_active` boolean NOT NULL DEFAULT true
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid

**Constraints**
- PK PRIMARY KEY (document_types_id)
- UNIQUE `document_types_code_key` UNIQUE (code)
- UNIQUE `document_types_name_key` UNIQUE (name)
- UNIQUE `document_types_uuid_key` UNIQUE (uuid)

**Indexes**
- `idx_document_types_is_active` btree (is_active) WHERE is_active

**Triggers**
- `trg_document_types_updated_at` BEFORE UPDATE ON app.document_types FOR EACH ROW EXECUTE FUNCTION set_updated_at()

## entity_config

> Per-entity behavior config: audit toggles (original V60 scope) + default list sort (V80). entity_key must match the "entity" attribute of the @Auditable annotation on the corresponding service method.

**Columns**
- `entity_config_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `entity_key` character varying(80) NOT NULL
- `display_name` character varying(120) NOT NULL
- `table_name` character varying(120)
- `enabled` boolean NOT NULL DEFAULT true
- `audit_create` boolean NOT NULL DEFAULT true
- `audit_update` boolean NOT NULL DEFAULT true
- `audit_delete` boolean NOT NULL DEFAULT true
- `audit_report` boolean NOT NULL DEFAULT true
- `capture_before_after` boolean NOT NULL DEFAULT true
- `notes` text
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid
- `default_sort` jsonb — Ordered array of {"field": "<sortable key>", "direction": "ASC"|"DESC"}, e.g. [{"field":"name","direction":"ASC"},{"field":"allyTypeName","direction":"ASC"}]. NULL falls back to the hard default createdAt DESC. "field" is the key the frontend table sends (matches SortFieldValidator.SORTABLE_FIELDS), not a raw JPA path.

**Constraints**
- PK PRIMARY KEY (entity_config_id)
- UNIQUE `audit_entity_config_entity_key_key` UNIQUE (entity_key)
- UNIQUE `audit_entity_config_uuid_key` UNIQUE (uuid)

**Triggers**
- `trg_audit_entity_config_updated_at` BEFORE UPDATE ON app.entity_config FOR EACH ROW EXECUTE FUNCTION set_updated_at()

## exchange_rates

**Columns**
- `exchange_rates_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `base_currency_id` bigint NOT NULL
- `quote_currency_id` bigint NOT NULL
- `rate` numeric(18,8) NOT NULL
- `operation_date` date NOT NULL
- `valid_from` timestamp with time zone NOT NULL
- `source` character varying(30) NOT NULL
- `fetched_at` timestamp with time zone NOT NULL DEFAULT now()
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50) NOT NULL DEFAULT 'ACTIVE'::character varying
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid

**Constraints**
- PK PRIMARY KEY (exchange_rates_id)
- FK `exchange_rates_base_currency_id_fkey` FOREIGN KEY (base_currency_id) REFERENCES currencies(currencies_id)
- FK `exchange_rates_quote_currency_id_fkey` FOREIGN KEY (quote_currency_id) REFERENCES currencies(currencies_id)
- UNIQUE `exchange_rates_uuid_key` UNIQUE (uuid)
- CHECK `chk_exchange_rates_currencies_distinct` CHECK ((base_currency_id <> quote_currency_id))
- CHECK `chk_exchange_rates_source` CHECK (((source)::text = ANY ((ARRAY['BCV'::character varying, 'EXCHANGE_RATES_API'::character varying, 'MANUAL'::character varying])::text[])))
- CHECK `exchange_rates_rate_check` CHECK ((rate > (0)::numeric))

**Indexes**
- `idx_exchange_rates_pair_valid_from` btree (base_currency_id, quote_currency_id, valid_from DESC)
- `uq_exchange_rates_pair_operation_date` UNIQUE btree (base_currency_id, quote_currency_id, operation_date)

**Triggers**
- `trg_exchange_rates_updated_at` BEFORE UPDATE ON app.exchange_rates FOR EACH ROW EXECUTE FUNCTION set_updated_at()

## file_download_links

**Columns**
- `file_download_links_id` bigint NOT NULL IDENTITY
- `token` uuid NOT NULL DEFAULT gen_random_uuid()
- `visibility` character varying(20) NOT NULL
- `resource_table` character varying(50) NOT NULL
- `resource_uuid` uuid NOT NULL
- `file_key` character varying(500) NOT NULL
- `expires_at` timestamp with time zone NOT NULL
- `revoked_at` timestamp with time zone
- `max_downloads` integer
- `download_count` integer NOT NULL DEFAULT 0
- `created_by` bigint
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `status` character varying(20) NOT NULL DEFAULT 'ACTIVE'::character varying

**Constraints**
- PK PRIMARY KEY (file_download_links_id)
- FK `file_download_links_created_by_fkey` FOREIGN KEY (created_by) REFERENCES users(users_id)
- UNIQUE `file_download_links_token_key` UNIQUE (token)
- CHECK `chk_file_download_links_revoked` CHECK ((((status)::text = 'REVOKED'::text) = (revoked_at IS NOT NULL)))
- CHECK `chk_file_download_links_status` CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'EXPIRED'::character varying, 'REVOKED'::character varying, 'EXHAUSTED'::character varying])::text[])))
- CHECK `file_download_links_max_downloads_check` CHECK (((max_downloads IS NULL) OR (max_downloads > 0)))
- CHECK `file_download_links_visibility_check` CHECK (((visibility)::text = ANY ((ARRAY['PUBLIC'::character varying, 'TEMPORARY'::character varying, 'INTERNAL'::character varying, 'CONFIDENTIAL'::character varying])::text[])))

**Indexes**
- `idx_file_download_links_active_file` btree (resource_table, resource_uuid, file_key) WHERE ((status)::text = 'ACTIVE'::text)
- `idx_file_download_links_status_expiry` btree (status, expires_at)

## file_type_policies

**Columns**
- `file_type_policies_id` bigint NOT NULL IDENTITY
- `visibility` character varying(20) NOT NULL
- `mode` character varying(10) NOT NULL
- `extensions` text[] NOT NULL
- `max_size_bytes` bigint NOT NULL
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_by` uuid

**Constraints**
- PK PRIMARY KEY (file_type_policies_id)
- UNIQUE `file_type_policies_visibility_key` UNIQUE (visibility)
- CHECK `file_type_policies_max_size_bytes_check` CHECK ((max_size_bytes > 0))
- CHECK `file_type_policies_mode_check` CHECK (((mode)::text = ANY ((ARRAY['ALLOWLIST'::character varying, 'DENYLIST'::character varying])::text[])))
- CHECK `file_type_policies_visibility_check` CHECK (((visibility)::text = ANY ((ARRAY['PUBLIC'::character varying, 'TEMPORARY'::character varying, 'INTERNAL'::character varying, 'CONFIDENTIAL'::character varying])::text[])))

**Triggers**
- `trg_file_type_policies_updated_at` BEFORE UPDATE ON app.file_type_policies FOR EACH ROW EXECUTE FUNCTION set_updated_at()

## genders

**Columns**
- `genders_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `code` character varying(1) NOT NULL
- `name` character varying(20) NOT NULL
- `is_active` boolean NOT NULL DEFAULT true
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid

**Constraints**
- PK PRIMARY KEY (genders_id)
- UNIQUE `genders_code_key` UNIQUE (code)
- UNIQUE `genders_name_key` UNIQUE (name)
- UNIQUE `genders_uuid_key` UNIQUE (uuid)

**Indexes**
- `idx_genders_is_active` btree (is_active) WHERE is_active

**Triggers**
- `trg_genders_updated_at` BEFORE UPDATE ON app.genders FOR EACH ROW EXECUTE FUNCTION set_updated_at()

**Referenced by** `persons`.(gender_id)

## hierarchy_override_tiers

**Columns**
- `hierarchy_override_tiers_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL
- `name` character varying(80) NOT NULL
- `rank_id` bigint NOT NULL
- `category` character varying(20) NOT NULL
- `threshold_count` integer NOT NULL DEFAULT 0
- `override_pct` numeric(5,2)
- `flat_amount` numeric(10,2)
- `flat_amount_currency_id` bigint
- `accrual_period_strategy` character varying(20) NOT NULL DEFAULT 'MONTHLY'::character varying
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50)
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid
- `partial_settlement_period_strategy` character varying(20) NOT NULL DEFAULT 'MONTHLY'::character varying
- `final_settlement_period_strategy` character varying(20) NOT NULL DEFAULT 'MONTHLY'::character varying
- `campaign_id` bigint
- `starts_at` timestamp with time zone
- `ends_at` timestamp with time zone
- `description` text
- `retroactive_settlement_period_strategy` character varying(20) NOT NULL DEFAULT 'MONTHLY'::character varying
- `accrual_period_anchor` smallint
- `partial_settlement_period_anchor` smallint
- `final_settlement_period_anchor` smallint
- `retroactive_settlement_period_anchor` smallint
- `basis` character varying(10) NOT NULL DEFAULT 'COUNT'::character varying
- `threshold_amount` numeric(14,2)
- `threshold_amount_currency_id` bigint

**Constraints**
- PK PRIMARY KEY (hierarchy_override_tiers_id)
- FK `hierarchy_override_tiers_campaign_id_fkey` FOREIGN KEY (campaign_id) REFERENCES campaigns(campaigns_id)
- FK `hierarchy_override_tiers_flat_amount_currency_id_fkey` FOREIGN KEY (flat_amount_currency_id) REFERENCES currencies(currencies_id)
- FK `hierarchy_override_tiers_rank_id_fkey` FOREIGN KEY (rank_id) REFERENCES promoter_ranks(promoter_ranks_id)
- FK `hierarchy_override_tiers_threshold_amount_currency_id_fkey` FOREIGN KEY (threshold_amount_currency_id) REFERENCES currencies(currencies_id)
- UNIQUE `hierarchy_override_tiers_uuid_key` UNIQUE (uuid)
- CHECK `chk_hierarchy_override_tiers_accrual_period_anchor_range` CHECK (((accrual_period_anchor IS NULL) OR ((accrual_period_anchor >= 1) AND (accrual_period_anchor <= 31))))
- CHECK `chk_hierarchy_override_tiers_accrual_period_strategy` CHECK (((accrual_period_strategy)::text = ANY ((ARRAY['DAILY'::character varying, 'WEEKLY'::character varying, 'BIWEEKLY'::character varying, 'MONTHLY'::character varying, 'QUARTERLY'::character varying, 'SEMIANNUAL'::character varying, 'ANNUAL'::character varying])::text[])))
- CHECK `chk_hierarchy_override_tiers_basis` CHECK (((basis)::text = ANY ((ARRAY['COUNT'::character varying, 'AMOUNT'::character varying])::text[])))
- CHECK `chk_hierarchy_override_tiers_basis_field` CHECK (((((basis)::text = 'COUNT'::text) AND (threshold_amount IS NULL) AND (threshold_amount_currency_id IS NULL)) OR (((basis)::text = 'AMOUNT'::text) AND (threshold_amount IS NOT NULL) AND (threshold_amount_currency_id IS NOT NULL))))
- CHECK `chk_hierarchy_override_tiers_category` CHECK (((category)::text = ANY ((ARRAY['INSCRIPTION'::character varying, 'COLLECTION'::character varying])::text[])))
- CHECK `chk_hierarchy_override_tiers_final_settlement_period_anchor_ran` CHECK (((final_settlement_period_anchor IS NULL) OR ((final_settlement_period_anchor >= 1) AND (final_settlement_period_anchor <= 31))))
- CHECK `chk_hierarchy_override_tiers_final_settlement_period_strategy` CHECK (((final_settlement_period_strategy)::text = ANY ((ARRAY['DAILY'::character varying, 'WEEKLY'::character varying, 'BIWEEKLY'::character varying, 'MONTHLY'::character varying, 'QUARTERLY'::character varying, 'SEMIANNUAL'::character varying, 'ANNUAL'::character varying])::text[])))
- CHECK `chk_hierarchy_override_tiers_flat_needs_currency` CHECK (((flat_amount IS NULL) OR (flat_amount_currency_id IS NOT NULL)))
- CHECK `chk_hierarchy_override_tiers_partial_settlement_period_anchor_r` CHECK (((partial_settlement_period_anchor IS NULL) OR ((partial_settlement_period_anchor >= 1) AND (partial_settlement_period_anchor <= 31))))
- CHECK `chk_hierarchy_override_tiers_partial_settlement_period_strategy` CHECK (((partial_settlement_period_strategy)::text = ANY ((ARRAY['DAILY'::character varying, 'WEEKLY'::character varying, 'BIWEEKLY'::character varying, 'MONTHLY'::character varying, 'QUARTERLY'::character varying, 'SEMIANNUAL'::character varying, 'ANNUAL'::character varying])::text[])))
- CHECK `chk_hierarchy_override_tiers_pct_xor_flat` CHECK (((override_pct IS NOT NULL) <> (flat_amount IS NOT NULL)))
- CHECK `chk_hierarchy_override_tiers_retroactive_settlement_period_anch` CHECK (((retroactive_settlement_period_anchor IS NULL) OR ((retroactive_settlement_period_anchor >= 1) AND (retroactive_settlement_period_anchor <= 31))))
- CHECK `chk_hierarchy_override_tiers_retroactive_settlement_period_stra` CHECK (((retroactive_settlement_period_strategy)::text = ANY ((ARRAY['DAILY'::character varying, 'WEEKLY'::character varying, 'BIWEEKLY'::character varying, 'MONTHLY'::character varying, 'QUARTERLY'::character varying, 'SEMIANNUAL'::character varying, 'ANNUAL'::character varying])::text[])))

**Indexes**
- `idx_hierarchy_override_tiers_campaign` btree (campaign_id) WHERE (campaign_id IS NOT NULL)
- `idx_hierarchy_override_tiers_rank_category` btree (rank_id, category, threshold_count DESC)

**Triggers**
- `trg_hierarchy_override_tiers_updated_at` BEFORE UPDATE ON app.hierarchy_override_tiers FOR EACH ROW EXECUTE FUNCTION set_updated_at()

**Referenced by** `promoter_hierarchy_overrides`.(tier_id)

## holidays

**Columns**
- `holidays_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `code` character varying(50)
- `name` character varying(150) NOT NULL
- `description` character varying(255)
- `holiday_date` date NOT NULL
- `is_recurring` boolean NOT NULL DEFAULT false
- `recurrence_type` character varying(12) NOT NULL DEFAULT 'NONE'::character varying
- `repetitions_count` integer NOT NULL DEFAULT 0
- `country_id` bigint
- `state_id` bigint
- `city_id` bigint
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50) NOT NULL DEFAULT 'ACTIVE'::character varying
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid

**Constraints**
- PK PRIMARY KEY (holidays_id)
- FK `holidays_city_id_fkey` FOREIGN KEY (city_id) REFERENCES cities(cities_id)
- FK `holidays_country_id_fkey` FOREIGN KEY (country_id) REFERENCES countries(countries_id)
- FK `holidays_state_id_fkey` FOREIGN KEY (state_id) REFERENCES states(states_id)
- UNIQUE `holidays_uuid_key` UNIQUE (uuid)
- CHECK `chk_holidays_city_requires_state` CHECK (((city_id IS NULL) OR (state_id IS NOT NULL)))
- CHECK `chk_holidays_recurrence_coherence` CHECK ((((is_recurring = false) AND ((recurrence_type)::text = 'NONE'::text)) OR ((is_recurring = true) AND ((recurrence_type)::text <> 'NONE'::text))))
- CHECK `chk_holidays_state_requires_country` CHECK (((state_id IS NULL) OR (country_id IS NOT NULL)))
- CHECK `holidays_recurrence_type_check` CHECK (((recurrence_type)::text = ANY ((ARRAY['NONE'::character varying, 'WEEKLY'::character varying, 'MONTHLY'::character varying, 'ANNUAL'::character varying])::text[])))
- CHECK `holidays_repetitions_count_check` CHECK ((repetitions_count >= 0))

**Indexes**
- `idx_holidays_is_active` btree (is_active) WHERE is_active
- `idx_holidays_scope` btree (country_id, state_id, city_id)

**Triggers**
- `trg_holidays_updated_at` BEFORE UPDATE ON app.holidays FOR EACH ROW EXECUTE FUNCTION set_updated_at()

## licensing_bodies

**Columns**
- `licensing_bodies_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `code` character varying(40) NOT NULL
- `name` character varying(150) NOT NULL
- `description` character varying(200)
- `professional_area` character varying(20)
- `is_active` boolean NOT NULL DEFAULT true
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid

**Constraints**
- PK PRIMARY KEY (licensing_bodies_id)
- UNIQUE `licensing_bodies_code_key` UNIQUE (code)
- UNIQUE `licensing_bodies_name_key` UNIQUE (name)
- UNIQUE `licensing_bodies_uuid_key` UNIQUE (uuid)
- CHECK `ck_licensing_bodies_professional_area` CHECK (((professional_area IS NULL) OR ((professional_area)::text = ANY ((ARRAY['MEDICAL'::character varying, 'WELLNESS'::character varying])::text[]))))

**Indexes**
- `idx_licensing_bodies_is_active` btree (is_active) WHERE is_active

**Referenced by** `person_professional_licenses`.(licensing_body_id)

## login_audit_log

**Columns**
- `login_audit_log_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `user_id` bigint
- `attempted_email` character varying(255) NOT NULL
- `result` character varying(20) NOT NULL
- `roles` jsonb
- `locale` character varying(10)
- `ip_address` inet
- `user_agent` character varying(500)
- `hostname` character varying(255)
- `jti` character varying(36)
- `failure_reason` character varying(255)
- `session_status` character varying(20)
- `session_expires_at` timestamp with time zone
- `is_valid` boolean NOT NULL DEFAULT true
- `logged_out_at` timestamp with time zone
- `logout_reason` character varying(50)
- `attempted_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `active_role_id` bigint

**Constraints**
- PK PRIMARY KEY (login_audit_log_id)
- FK `login_audit_log_active_role_id_fkey` FOREIGN KEY (active_role_id) REFERENCES roles(roles_id)
- FK `login_audit_log_user_id_fkey` FOREIGN KEY (user_id) REFERENCES users(users_id)
- UNIQUE `login_audit_log_uuid_key` UNIQUE (uuid)
- CHECK `chk_login_audit_logout_reason` CHECK (((logout_reason)::text = ANY ((ARRAY['user_logout'::character varying, 'role_switch'::character varying, 'password_reset'::character varying, 'password_change'::character varying, 'token_expired'::character varying, 'forced'::character varying])::text[])))
- CHECK `chk_login_audit_result` CHECK (((result)::text = ANY ((ARRAY['SUCCESS'::character varying, 'FAILED_CREDENTIALS'::character varying, 'FAILED_LOCKED'::character varying, 'FAILED_INACTIVE'::character varying])::text[])))
- CHECK `chk_login_audit_session_status` CHECK (((session_status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'LOGGED_OUT'::character varying, 'EXPIRED'::character varying, 'REVOKED'::character varying])::text[])))

**Indexes**
- `idx_login_audit_active_expiry` btree (session_expires_at) WHERE ((session_status)::text = 'ACTIVE'::text)
- `idx_login_audit_active_user` btree (user_id) WHERE ((session_status)::text = 'ACTIVE'::text)
- `idx_login_audit_attempted_at` btree (attempted_at DESC)
- `idx_login_audit_created_at` btree (created_at DESC)
- `idx_login_audit_email_attempted` btree (attempted_email, attempted_at DESC)
- `idx_login_audit_failed` btree (attempted_at DESC) WHERE ((result)::text <> 'SUCCESS'::text)
- `idx_login_audit_user_attempted` btree (user_id, attempted_at DESC)

**Referenced by** `data_change_audit_log`.(login_audit_log_id), `report_audit_log`.(login_audit_log_id)

## marital_statuses

**Columns**
- `marital_statuses_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `code` character varying(20) NOT NULL
- `name` character varying(50) NOT NULL
- `is_active` boolean NOT NULL DEFAULT true
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid

**Constraints**
- PK PRIMARY KEY (marital_statuses_id)
- UNIQUE `marital_statuses_code_key` UNIQUE (code)
- UNIQUE `marital_statuses_name_key` UNIQUE (name)
- UNIQUE `marital_statuses_uuid_key` UNIQUE (uuid)

**Indexes**
- `idx_marital_statuses_is_active` btree (is_active) WHERE is_active

**Triggers**
- `trg_marital_statuses_updated_at` BEFORE UPDATE ON app.marital_statuses FOR EACH ROW EXECUTE FUNCTION set_updated_at()

**Referenced by** `persons`.(marital_status_id)

## media_items

> Lista ordenada de imágenes de un dueño (aliado, producto, oferta…), con una principal; archivo en R2 o URL externa (ADR 0027).

**Columns**
- `media_items_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `owner_table` character varying(50) NOT NULL
- `owner_uuid` uuid NOT NULL
- `attached_file_id` bigint
- `public_key` character varying(500)
- `external_url` character varying(500)
- `caption` character varying(200)
- `sort_order` integer NOT NULL DEFAULT 0
- `is_primary` boolean NOT NULL DEFAULT false
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50)
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid

**Constraints**
- PK PRIMARY KEY (media_items_id)
- FK `media_items_attached_file_id_fkey` FOREIGN KEY (attached_file_id) REFERENCES attached_files(attached_files_id) ON DELETE SET NULL
- UNIQUE `media_items_uuid_key` UNIQUE (uuid)
- CHECK `chk_media_items_one_source` CHECK (((public_key IS NULL) <> (external_url IS NULL)))

**Indexes**
- `idx_media_items_owner` btree (owner_table, owner_uuid, sort_order) WHERE is_active
- `uq_media_items_one_primary` UNIQUE btree (owner_table, owner_uuid) WHERE (is_primary AND is_active)

**Triggers**
- `trg_media_items_updated_at` BEFORE UPDATE ON app.media_items FOR EACH ROW EXECUTE FUNCTION set_updated_at()

## medical_record_access_log

> Bitácora de solo-agregar de cada lectura de historia médica por un aliado médico (ADR 0032). No se purga.

**Columns**
- `medical_record_access_log_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `link_id` bigint NOT NULL
- `accessed_by_user_id` bigint NOT NULL
- `ally_id` bigint NOT NULL
- `member_id` bigint NOT NULL
- `accessed_at` timestamp with time zone NOT NULL DEFAULT now()

**Constraints**
- PK PRIMARY KEY (medical_record_access_log_id)
- FK `medical_record_access_log_accessed_by_user_id_fkey` FOREIGN KEY (accessed_by_user_id) REFERENCES users(users_id)
- FK `medical_record_access_log_ally_id_fkey` FOREIGN KEY (ally_id) REFERENCES allies(allies_id)
- FK `medical_record_access_log_link_id_fkey` FOREIGN KEY (link_id) REFERENCES ally_patient_links(ally_patient_links_id)
- FK `medical_record_access_log_member_id_fkey` FOREIGN KEY (member_id) REFERENCES members(members_id)
- UNIQUE `medical_record_access_log_uuid_key` UNIQUE (uuid)

**Indexes**
- `idx_medical_record_access_log_member` btree (member_id, accessed_at DESC)

## medical_records

**Columns**
- `medical_records_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `person_id` bigint NOT NULL
- `blood_type` character varying(5)
- `allergies` text
- `chronic_conditions` text
- `current_medications` text
- `emergency_contact_name` character varying(200)
- `emergency_contact_phone` character varying(30)
- `emergency_contact_relationship` character varying(20)
- `notes` text
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50)
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid

**Constraints**
- PK PRIMARY KEY (medical_records_id)
- FK `medical_records_person_id_fkey` FOREIGN KEY (person_id) REFERENCES persons(persons_id)
- UNIQUE `medical_records_person_id_key` UNIQUE (person_id)
- UNIQUE `medical_records_uuid_key` UNIQUE (uuid)
- CHECK `medical_records_blood_type_check` CHECK (((blood_type)::text = ANY ((ARRAY['A+'::character varying, 'A-'::character varying, 'B+'::character varying, 'B-'::character varying, 'AB+'::character varying, 'AB-'::character varying, 'O+'::character varying, 'O-'::character varying])::text[])))
- CHECK `medical_records_emergency_contact_relationship_check` CHECK (((emergency_contact_relationship)::text = ANY ((ARRAY['SPOUSE'::character varying, 'CHILD'::character varying, 'PARENT'::character varying, 'SIBLING'::character varying, 'FRIEND'::character varying, 'OTHER'::character varying])::text[])))

**Triggers**
- `trg_medical_records_updated_at` BEFORE UPDATE ON app.medical_records FOR EACH ROW EXECUTE FUNCTION set_updated_at()

## member_documents

**Columns**
- `member_documents_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `member_id` bigint NOT NULL
- `document_type` character varying(50) NOT NULL
- `file_url` character varying(500) NOT NULL
- `file_name` character varying(255) NOT NULL
- `file_size_bytes` bigint NOT NULL
- `mime_type` character varying(100) NOT NULL
- `uploaded_by` bigint
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50)
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid

**Constraints**
- PK PRIMARY KEY (member_documents_id)
- FK `member_documents_member_id_fkey` FOREIGN KEY (member_id) REFERENCES members(members_id) ON DELETE CASCADE
- FK `member_documents_uploaded_by_fkey` FOREIGN KEY (uploaded_by) REFERENCES users(users_id)
- UNIQUE `member_documents_uuid_key` UNIQUE (uuid)
- CHECK `member_documents_document_type_check` CHECK (((document_type)::text = ANY ((ARRAY['ID_FRONT'::character varying, 'ID_BACK'::character varying, 'PROOF_OF_RESIDENCE'::character varying, 'MEDICAL_HISTORY'::character varying, 'MEMBER_PHOTO'::character varying, 'INCOME_PROOF'::character varying, 'OTHER'::character varying])::text[])))
- CHECK `member_documents_file_size_bytes_check` CHECK ((file_size_bytes > 0))

**Indexes**
- `idx_member_documents_member_type` btree (member_id, document_type)

**Triggers**
- `trg_member_documents_updated_at` BEFORE UPDATE ON app.member_documents FOR EACH ROW EXECUTE FUNCTION set_updated_at()

## member_promoter_assignments

**Columns**
- `member_promoter_assignments_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL
- `member_id` bigint NOT NULL
- `from_promoter_id` bigint
- `to_promoter_id` bigint NOT NULL
- `actor_user_id` bigint
- `reason` text NOT NULL
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50)
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid

**Constraints**
- PK PRIMARY KEY (member_promoter_assignments_id)
- FK `member_promoter_assignments_actor_user_id_fkey` FOREIGN KEY (actor_user_id) REFERENCES users(users_id)
- FK `member_promoter_assignments_from_promoter_id_fkey` FOREIGN KEY (from_promoter_id) REFERENCES promoters(promoters_id)
- FK `member_promoter_assignments_member_id_fkey` FOREIGN KEY (member_id) REFERENCES members(members_id)
- FK `member_promoter_assignments_to_promoter_id_fkey` FOREIGN KEY (to_promoter_id) REFERENCES promoters(promoters_id)
- UNIQUE `member_promoter_assignments_uuid_key` UNIQUE (uuid)

**Indexes**
- `idx_member_promoter_assignments_member` btree (member_id, created_at DESC)

## members

**Columns**
- `members_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `person_id` bigint NOT NULL
- `occupation_id` bigint
- `enrolled_at` date NOT NULL DEFAULT CURRENT_DATE
- `notes` text
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50)
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid
- `promoter_id` bigint
- `referral_code` character varying(20)
- `employer_name` character varying(150)
- `job_position` character varying(100)
- `employer_address` text
- `corporate_contract_id` bigint
- `confirmed_at` timestamp with time zone — Set automatically on the member's first approved payment, or manually by an admin (MEMBER_CONFIRM) for subsidized members that never pay. NULL = pending confirmation.

**Constraints**
- PK PRIMARY KEY (members_id)
- FK `members_corporate_contract_id_fkey` FOREIGN KEY (corporate_contract_id) REFERENCES corporate_contracts(corporate_contracts_id)
- FK `members_occupation_id_fkey` FOREIGN KEY (occupation_id) REFERENCES occupations(occupations_id)
- FK `members_person_id_fkey` FOREIGN KEY (person_id) REFERENCES persons(persons_id)
- FK `members_promoter_id_fkey` FOREIGN KEY (promoter_id) REFERENCES promoters(promoters_id)
- UNIQUE `members_person_id_key` UNIQUE (person_id)
- UNIQUE `members_uuid_key` UNIQUE (uuid)

**Indexes**
- `idx_members_active_created` btree (created_at DESC) WHERE is_active
- `idx_members_corporate_contract` btree (corporate_contract_id) WHERE (corporate_contract_id IS NOT NULL)
- `idx_members_enrolled_at` btree (enrolled_at)
- `idx_members_occupation` btree (occupation_id) WHERE (occupation_id IS NOT NULL)
- `idx_members_promoter_id` btree (promoter_id) WHERE (promoter_id IS NOT NULL)
- `uniq_members_referral_code` UNIQUE btree (referral_code) WHERE (referral_code IS NOT NULL)

**Triggers**
- `trg_members_updated_at` BEFORE UPDATE ON app.members FOR EACH ROW EXECUTE FUNCTION set_updated_at()

**Referenced by** `affiliation_requests`.(member_id), `ally_patient_links`.(member_id), `beneficiaries`.(member_id), `commissions`.(member_id), `medical_record_access_log`.(member_id), `member_documents`.(member_id), `member_promoter_assignments`.(member_id), `membership_promotions`.(code_owner_member_id), `memberships`.(member_id), `promoter_member_contacts`.(member_id), `referrals`.(referred_member_id), `referrals`.(referrer_member_id), `subsidies`.(member_id)

## membership_charges

**Columns**
- `membership_charges_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `membership_id` bigint NOT NULL
- `period_start` date NOT NULL
- `period_end` date NOT NULL
- `due_date` date NOT NULL
- `amount` numeric(10,2) NOT NULL
- `currency_id` bigint NOT NULL
- `covered_by_payment_id` bigint
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50) NOT NULL DEFAULT 'PENDING'::character varying
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid
- `gross_amount` numeric(10,2) NOT NULL
- `discount_amount` numeric(10,2) NOT NULL DEFAULT 0
- `discount_source` character varying(20)
- `membership_promotion_id` bigint

**Constraints**
- PK PRIMARY KEY (membership_charges_id)
- FK `membership_charges_covered_by_payment_id_fkey` FOREIGN KEY (covered_by_payment_id) REFERENCES payments(payments_id)
- FK `membership_charges_currency_id_fkey` FOREIGN KEY (currency_id) REFERENCES currencies(currencies_id)
- FK `membership_charges_membership_id_fkey` FOREIGN KEY (membership_id) REFERENCES memberships(memberships_id)
- FK `membership_charges_membership_promotion_id_fkey` FOREIGN KEY (membership_promotion_id) REFERENCES membership_promotions(membership_promotions_id)
- UNIQUE `membership_charges_uuid_key` UNIQUE (uuid)
- CHECK `chk_membership_charges_discount_source` CHECK (((discount_source IS NULL) OR ((discount_source)::text = ANY ((ARRAY['PROMOTION'::character varying, 'SUBSIDY'::character varying])::text[]))))
- CHECK `chk_membership_charges_net` CHECK (((discount_amount >= (0)::numeric) AND (discount_amount <= gross_amount) AND (amount = (gross_amount - discount_amount))))
- CHECK `chk_membership_charges_period_end_after_start` CHECK ((period_end >= period_start))
- CHECK `membership_charges_amount_check` CHECK ((amount >= (0)::numeric))
- CHECK `membership_charges_status_check` CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'COVERED'::character varying, 'OVERDUE'::character varying, 'WAIVED'::character varying])::text[])))

**Indexes**
- `idx_membership_charges_covered_by_payment` btree (covered_by_payment_id) WHERE (covered_by_payment_id IS NOT NULL)
- `uniq_membership_charges_membership_period` UNIQUE btree (membership_id, period_start)

**Triggers**
- `trg_membership_charges_updated_at` BEFORE UPDATE ON app.membership_charges FOR EACH ROW EXECUTE FUNCTION set_updated_at()

**Referenced by** `person_credit_entries`.(charge_id)

## membership_promotions

**Columns**
- `membership_promotions_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL
- `membership_id` bigint NOT NULL
- `promotion_id` bigint NOT NULL
- `origin` character varying(20) NOT NULL
- `code_used` character varying(20)
- `code_owner_promoter_id` bigint
- `code_owner_member_id` bigint
- `code_owner_ally_id` bigint
- `cycles_remaining` integer
- `inscription_applied` boolean NOT NULL DEFAULT false
- `referrer_reward_granted` boolean NOT NULL DEFAULT false
- `assigned_by_user_id` bigint
- `assigned_at` timestamp with time zone NOT NULL DEFAULT now()
- `ended_at` timestamp with time zone
- `ended_reason` text
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50) NOT NULL DEFAULT 'ACTIVE'::character varying
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid

**Constraints**
- PK PRIMARY KEY (membership_promotions_id)
- FK `membership_promotions_assigned_by_user_id_fkey` FOREIGN KEY (assigned_by_user_id) REFERENCES users(users_id)
- FK `membership_promotions_code_owner_ally_id_fkey` FOREIGN KEY (code_owner_ally_id) REFERENCES allies(allies_id)
- FK `membership_promotions_code_owner_member_id_fkey` FOREIGN KEY (code_owner_member_id) REFERENCES members(members_id)
- FK `membership_promotions_code_owner_promoter_id_fkey` FOREIGN KEY (code_owner_promoter_id) REFERENCES promoters(promoters_id)
- FK `membership_promotions_membership_id_fkey` FOREIGN KEY (membership_id) REFERENCES memberships(memberships_id)
- FK `membership_promotions_promotion_id_fkey` FOREIGN KEY (promotion_id) REFERENCES promotions(promotions_id)
- UNIQUE `membership_promotions_uuid_key` UNIQUE (uuid)
- CHECK `chk_membership_promotions_cycles` CHECK (((cycles_remaining IS NULL) OR (cycles_remaining >= 0)))
- CHECK `chk_membership_promotions_ended` CHECK ((((status)::text = 'ACTIVE'::text) OR (ended_at IS NOT NULL)))
- CHECK `chk_membership_promotions_origin` CHECK (((origin)::text = ANY ((ARRAY['ENROLLMENT'::character varying, 'ADMIN'::character varying])::text[])))
- CHECK `chk_membership_promotions_single_owner` CHECK ((num_nonnulls(code_owner_promoter_id, code_owner_member_id, code_owner_ally_id) <= 1))
- CHECK `chk_membership_promotions_status` CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'CONSUMED'::character varying, 'CANCELED'::character varying])::text[])))

**Indexes**
- `idx_membership_promotions_promotion` btree (promotion_id)
- `uq_membership_promotions_one_active` UNIQUE btree (membership_id) WHERE (((status)::text = 'ACTIVE'::text) AND is_active)

**Triggers**
- `trg_membership_promotions_updated_at` BEFORE UPDATE ON app.membership_promotions FOR EACH ROW EXECUTE FUNCTION set_updated_at()

**Referenced by** `membership_charges`.(membership_promotion_id)

## memberships

**Columns**
- `memberships_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `member_id` bigint NOT NULL
- `plan_id` bigint NOT NULL
- `enrolled_at` date NOT NULL DEFAULT CURRENT_DATE
- `expires_at` date
- `next_due_date` date NOT NULL
- `last_paid_through` date
- `inscription_fee` numeric(10,2) NOT NULL
- `monthly_fee` numeric(10,2) NOT NULL
- `grace_period_days` integer NOT NULL
- `last_status_change_at` timestamp with time zone
- `last_status_change_reason` text
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50) NOT NULL DEFAULT 'ACTIVE'::character varying
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid
- `billing_start_day` integer — Billing cutover day of month (1-28). NULL = derive from enrolled_at day-of-month. Anchors the collection-commission engine's scheduled collection date.
- `currency_id` bigint NOT NULL
- `campaign_id` bigint
- `contract_number` character varying(20) NOT NULL — Número de contrato legible (OB-000123), asignado al inscribir; es lo que cita quien paga.
- `reinscription_required` boolean NOT NULL DEFAULT false — Snapshot del plan (editable por admin): exige reinscripción en cada vigencia.
- `term_mode` character varying(20)
- `term_fixed_month` integer
- `term_fixed_day` integer
- `term_period_months` integer
- `term_started_at` date — Inicio de la vigencia actual; expires_at es su fin.
- `inscription_due_since` date — Fecha desde la que hay una reinscripción pendiente; la membresía queda SUSPENDED hasta aplicar un pago de inscripción aprobado.

**Constraints**
- PK PRIMARY KEY (memberships_id)
- FK `memberships_campaign_id_fkey` FOREIGN KEY (campaign_id) REFERENCES campaigns(campaigns_id)
- FK `memberships_currency_id_fkey` FOREIGN KEY (currency_id) REFERENCES currencies(currencies_id)
- FK `memberships_member_id_fkey` FOREIGN KEY (member_id) REFERENCES members(members_id)
- FK `memberships_plan_id_fkey` FOREIGN KEY (plan_id) REFERENCES plans(plans_id)
- UNIQUE `memberships_uuid_key` UNIQUE (uuid)
- CHECK `chk_memberships_billing_start_day` CHECK (((billing_start_day >= 1) AND (billing_start_day <= 28)))
- CHECK `chk_memberships_expires_after_enrolled` CHECK (((expires_at IS NULL) OR (expires_at >= enrolled_at)))
- CHECK `chk_memberships_inscription_due` CHECK (((inscription_due_since IS NULL) OR (reinscription_required = true)))
- CHECK `chk_memberships_next_due_after_enrolled` CHECK ((next_due_date >= enrolled_at))
- CHECK `chk_memberships_paid_through_after_enrolled` CHECK (((last_paid_through IS NULL) OR (last_paid_through >= enrolled_at)))
- CHECK `chk_memberships_term_coherence` CHECK ((((reinscription_required = false) AND (term_mode IS NULL) AND (term_fixed_month IS NULL) AND (term_fixed_day IS NULL) AND (term_period_months IS NULL)) OR ((reinscription_required = true) AND ((term_mode)::text = 'FIXED_DATE'::text) AND (term_fixed_month IS NOT NULL) AND (term_fixed_day IS NOT NULL) AND (term_period_months IS NULL)) OR ((reinscription_required = true) AND ((term_mode)::text = 'PERIOD'::text) AND (term_period_months IS NOT NULL) AND (term_fixed_month IS NULL) AND (term_fixed_day IS NULL))))
- CHECK `chk_memberships_term_day` CHECK (((term_fixed_day IS NULL) OR ((term_fixed_day >= 1) AND (term_fixed_day <= 28))))
- CHECK `chk_memberships_term_mode` CHECK (((term_mode IS NULL) OR ((term_mode)::text = ANY ((ARRAY['FIXED_DATE'::character varying, 'PERIOD'::character varying])::text[]))))
- CHECK `chk_memberships_term_month` CHECK (((term_fixed_month IS NULL) OR ((term_fixed_month >= 1) AND (term_fixed_month <= 12))))
- CHECK `chk_memberships_term_period` CHECK (((term_period_months IS NULL) OR (term_period_months > 0)))
- CHECK `memberships_grace_period_days_check` CHECK ((grace_period_days >= 0))
- CHECK `memberships_inscription_fee_check` CHECK ((inscription_fee >= (0)::numeric))
- CHECK `memberships_monthly_fee_check` CHECK ((monthly_fee >= (0)::numeric))
- CHECK `memberships_status_check` CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'SUSPENDED'::character varying, 'EXPIRED'::character varying, 'CANCELED'::character varying])::text[])))

**Indexes**
- `idx_memberships_campaign` btree (campaign_id) WHERE (campaign_id IS NOT NULL)
- `idx_memberships_currency` btree (currency_id)
- `idx_memberships_member` btree (member_id)
- `idx_memberships_next_due` btree (next_due_date)
- `idx_memberships_status_next_due` btree (status, next_due_date) WHERE (is_active = true)
- `idx_memberships_term_expiry` btree (expires_at) WHERE ((is_active = true) AND (reinscription_required = true))
- `uniq_memberships_one_active_per_member` UNIQUE btree (member_id) WHERE (is_active = true)
- `uq_memberships_contract_number` UNIQUE btree (contract_number)

**Triggers**
- `trg_memberships_contract_number` BEFORE INSERT ON app.memberships FOR EACH ROW EXECUTE FUNCTION set_membership_contract_number()
- `trg_memberships_updated_at` BEFORE UPDATE ON app.memberships FOR EACH ROW EXECUTE FUNCTION set_updated_at()

**Referenced by** `affiliation_requests`.(membership_id), `benefit_usages`.(membership_id), `campaign_transaction_exceptions`.(membership_id), `campaign_transaction_links`.(membership_id), `membership_charges`.(membership_id), `membership_promotions`.(membership_id), `payments`.(membership_id), `person_credit_entries`.(membership_id), `terms_acceptances`.(membership_id)

## notifications

**Columns**
- `notifications_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `channel` character varying(20) NOT NULL DEFAULT 'EMAIL'::character varying
- `recipient_email` citext NOT NULL
- `recipient_user_id` bigint
- `recipient_locale` character varying(10) NOT NULL DEFAULT 'es'::character varying
- `template_code` character varying(80) NOT NULL
- `subject` character varying(200) NOT NULL
- `template_vars` jsonb NOT NULL DEFAULT '{}'::jsonb
- `source_module` character varying(40)
- `source_entity_uuid` uuid
- `status` character varying(20) NOT NULL DEFAULT 'PENDING'::character varying
- `scheduled_for` timestamp with time zone NOT NULL DEFAULT now()
- `attempt_count` integer NOT NULL DEFAULT 0
- `max_attempts` integer NOT NULL DEFAULT 5
- `last_attempt_at` timestamp with time zone
- `next_retry_at` timestamp with time zone
- `last_error_message` text
- `sent_at` timestamp with time zone
- `is_active` boolean NOT NULL DEFAULT true
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid

**Constraints**
- PK PRIMARY KEY (notifications_id)
- FK `notifications_recipient_user_id_fkey` FOREIGN KEY (recipient_user_id) REFERENCES users(users_id)
- UNIQUE `notifications_uuid_key` UNIQUE (uuid)
- CHECK `chk_notifications_attempt_bounds` CHECK ((attempt_count <= max_attempts))
- CHECK `chk_notifications_dead_letter_exhausted` CHECK ((((status)::text <> 'DEAD_LETTER'::text) OR (attempt_count >= max_attempts)))
- CHECK `chk_notifications_failed_has_error` CHECK ((((status)::text <> ALL ((ARRAY['FAILED'::character varying, 'DEAD_LETTER'::character varying])::text[])) OR (last_error_message IS NOT NULL)))
- CHECK `chk_notifications_sent_has_sent_at` CHECK ((((status)::text <> 'SENT'::text) OR (sent_at IS NOT NULL)))
- CHECK `notifications_attempt_count_check` CHECK ((attempt_count >= 0))
- CHECK `notifications_channel_check` CHECK (((channel)::text = 'EMAIL'::text))
- CHECK `notifications_max_attempts_check` CHECK ((max_attempts >= 1))
- CHECK `notifications_status_check` CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'SENDING'::character varying, 'SENT'::character varying, 'FAILED'::character varying, 'DEAD_LETTER'::character varying])::text[])))

**Indexes**
- `idx_notifications_dead_letter` btree (created_at) WHERE ((status)::text = 'DEAD_LETTER'::text)
- `idx_notifications_pending_due` btree (scheduled_for) WHERE (((status)::text = 'PENDING'::text) AND is_active)
- `idx_notifications_recipient_created` btree (recipient_user_id, created_at DESC) WHERE ((recipient_user_id IS NOT NULL) AND is_active)
- `idx_notifications_retry_due` btree (next_retry_at) WHERE (((status)::text = 'FAILED'::text) AND (next_retry_at IS NOT NULL) AND is_active)
- `idx_notifications_source` btree (source_module, source_entity_uuid, template_code) WHERE (source_entity_uuid IS NOT NULL)

**Triggers**
- `trg_notifications_updated_at` BEFORE UPDATE ON app.notifications FOR EACH ROW EXECUTE FUNCTION set_updated_at()

## occupations

**Columns**
- `occupations_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `name` character varying(100) NOT NULL
- `description` character varying(200)
- `is_active` boolean NOT NULL DEFAULT true
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid

**Constraints**
- PK PRIMARY KEY (occupations_id)
- UNIQUE `occupations_name_key` UNIQUE (name)
- UNIQUE `occupations_uuid_key` UNIQUE (uuid)

**Indexes**
- `idx_occupations_is_active` btree (is_active) WHERE is_active

**Triggers**
- `trg_occupations_updated_at` BEFORE UPDATE ON app.occupations FOR EACH ROW EXECUTE FUNCTION set_updated_at()

**Referenced by** `members`.(occupation_id)

## onboarding_policies

> Single active row: which self-registrations are open and how requests are approved and activated (hub ADR 0037).

**Columns**
- `onboarding_policies_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `allow_member_self_registration` boolean NOT NULL DEFAULT true
- `allow_ally_self_registration` boolean NOT NULL DEFAULT true
- `member_approval_mode` character varying(30) NOT NULL DEFAULT 'MANUAL'::character varying
- `member_activation_mode` character varying(30) NOT NULL DEFAULT 'ON_PAYMENT_APPROVED'::character varying
- `ally_approval_mode` character varying(30) NOT NULL DEFAULT 'MANUAL_WITH_AGREEMENT'::character varying
- `ally_publish_mode` character varying(30) NOT NULL DEFAULT 'MANUAL'::character varying
- `is_active` boolean NOT NULL DEFAULT true
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid
- `member_draft_retention_days` integer NOT NULL DEFAULT 15 — Días sin cambios tras los cuales se descarta un borrador de afiliación abandonado
- `ally_draft_retention_days` integer NOT NULL DEFAULT 30 — Días sin cambios tras los cuales se descarta un borrador de aliado abandonado

**Constraints**
- PK PRIMARY KEY (onboarding_policies_id)
- UNIQUE `onboarding_policies_uuid_key` UNIQUE (uuid)
- CHECK `chk_onboarding_policies_ally_approval_mode` CHECK (((ally_approval_mode)::text = ANY ((ARRAY['MANUAL_WITH_AGREEMENT'::character varying, 'AUTO_ON_SUBMIT'::character varying])::text[])))
- CHECK `chk_onboarding_policies_ally_draft_retention` CHECK ((ally_draft_retention_days >= 1))
- CHECK `chk_onboarding_policies_ally_publish_mode` CHECK (((ally_publish_mode)::text = ANY ((ARRAY['MANUAL'::character varying, 'ON_APPROVAL'::character varying])::text[])))
- CHECK `chk_onboarding_policies_member_activation_mode` CHECK (((member_activation_mode)::text = ANY ((ARRAY['ON_PAYMENT_APPROVED'::character varying, 'ON_PAYMENT_REPORTED'::character varying, 'IMMEDIATE'::character varying])::text[])))
- CHECK `chk_onboarding_policies_member_approval_mode` CHECK (((member_approval_mode)::text = ANY ((ARRAY['MANUAL'::character varying, 'AUTO_IF_PAYMENT_REPORTED'::character varying, 'AUTO_ON_SUBMIT'::character varying])::text[])))
- CHECK `chk_onboarding_policies_member_draft_retention` CHECK ((member_draft_retention_days >= 1))

**Triggers**
- `trg_onboarding_policies_updated_at` BEFORE UPDATE ON app.onboarding_policies FOR EACH ROW EXECUTE FUNCTION set_updated_at()

## organizations

**Columns**
- `organizations_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `name` character varying(150) NOT NULL
- `legal_name` character varying(200)
- `tax_identifier` character varying(20)
- `logo_key` character varying(255)
- `official_currency_id` bigint NOT NULL
- `reference_currency_id` bigint NOT NULL
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50) NOT NULL DEFAULT 'ACTIVE'::character varying
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid
- `whatsapp` character varying(30)
- `instagram` character varying(255)
- `facebook` character varying(255)
- `auto_approve_commissions` boolean NOT NULL DEFAULT false
- `registry_state` character varying(80) — Estado del Registro Mercantil donde está inscrita la organización ({{org.registryState}} en los contratos)
- `signing_city` character varying(80) — Ciudad de firma de los contratos ({{org.city}})
- `service_area` character varying(150) — Zona de cobertura de la red de aliados ({{org.serviceArea}})
- `address` text — Dirección de la organización ({{org.address}}, pie institucional de los contratos)
- `email` citext — Correo de contacto de la organización ({{org.email}})
- `phones` character varying(100) — Teléfonos de contacto tal como se imprimen en los contratos ({{org.phones}})
- `signatory_name` character varying(200) — Nombre del firmante autorizado de la empresa ({{org.signatoryName}})
- `signatory_document_type` character varying(1) — Tipo de documento del firmante: J, V, E, G o P
- `signatory_document_number` character varying(20) — Número de documento del firmante; con el tipo forma {{org.signatoryDocument}}
- `signatory_position` character varying(100) — Cargo del firmante autorizado ({{org.signatoryPosition}})

**Constraints**
- PK PRIMARY KEY (organizations_id)
- FK `organizations_official_currency_id_fkey` FOREIGN KEY (official_currency_id) REFERENCES currencies(currencies_id)
- FK `organizations_reference_currency_id_fkey` FOREIGN KEY (reference_currency_id) REFERENCES currencies(currencies_id)
- UNIQUE `organizations_uuid_key` UNIQUE (uuid)
- CHECK `chk_organizations_currencies_distinct` CHECK ((official_currency_id <> reference_currency_id))
- CHECK `chk_organizations_signatory_document` CHECK ((((signatory_document_type IS NULL) AND (signatory_document_number IS NULL)) OR ((signatory_document_type IS NOT NULL) AND (signatory_document_number IS NOT NULL))))
- CHECK `chk_organizations_signatory_document_type` CHECK (((signatory_document_type IS NULL) OR ((signatory_document_type)::text = ANY ((ARRAY['J'::character varying, 'V'::character varying, 'E'::character varying, 'G'::character varying, 'P'::character varying])::text[]))))

**Indexes**
- `idx_organizations_is_active` btree (is_active) WHERE is_active

**Triggers**
- `trg_organizations_updated_at` BEFORE UPDATE ON app.organizations FOR EACH ROW EXECUTE FUNCTION set_updated_at()

## payer_bank_account_payment_methods

> Métodos de pago que acepta cada cuenta propia de una persona; lo que exige la cuenta es la suma de los is_mandatory_* de sus métodos.

**Columns**
- `payer_bank_account_id` bigint NOT NULL
- `payment_method_id` bigint NOT NULL

**Constraints**
- PK PRIMARY KEY (payer_bank_account_id, payment_method_id)
- FK `payer_bank_account_payment_methods_payer_bank_account_id_fkey` FOREIGN KEY (payer_bank_account_id) REFERENCES payer_bank_accounts(payer_bank_accounts_id) ON DELETE CASCADE
- FK `payer_bank_account_payment_methods_payment_method_id_fkey` FOREIGN KEY (payment_method_id) REFERENCES payment_methods(payment_methods_id)

**Indexes**
- `idx_payer_account_methods_method` btree (payment_method_id)

## payer_bank_accounts

> Cuentas de origen que quien paga decidió recordar para no reescribirlas en el siguiente pago (hoy solo personas).

**Columns**
- `payer_bank_accounts_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `person_id` bigint NOT NULL
- `bank_id` bigint
- `account_type` character varying(40)
- `account_code` character varying(40)
- `account_identifier` character varying(120)
- `identification` character varying(80)
- `alias` character varying(80)
- `last_used_at` timestamp with time zone NOT NULL DEFAULT now()
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50)
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid
- `phone` character varying(30) — Teléfono de la cuenta (pago móvil).
- `email` character varying(160) — Correo de la cuenta (Zelle y similares).

**Constraints**
- PK PRIMARY KEY (payer_bank_accounts_id)
- FK `payer_bank_accounts_bank_id_fkey` FOREIGN KEY (bank_id) REFERENCES banks(banks_id)
- FK `payer_bank_accounts_person_id_fkey` FOREIGN KEY (person_id) REFERENCES persons(persons_id)
- UNIQUE `payer_bank_accounts_uuid_key` UNIQUE (uuid)

**Indexes**
- `idx_payer_bank_accounts_person` btree (person_id, last_used_at DESC) WHERE is_active
- `uq_payer_bank_accounts_identity` UNIQUE btree (person_id, COALESCE(bank_id, (0)::bigint), COALESCE(account_identifier, phone, email, ''::character varying)) WHERE is_active

**Triggers**
- `trg_payer_bank_accounts_updated_at` BEFORE UPDATE ON app.payer_bank_accounts FOR EACH ROW EXECUTE FUNCTION set_updated_at()

**Referenced by** `payer_bank_account_payment_methods`.(payer_bank_account_id)

## payment_categories

**Columns**
- `payment_categories_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `code` character varying(40) NOT NULL
- `name` character varying(80) NOT NULL
- `description` character varying(255)
- `direction` character varying(10) NOT NULL
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50) NOT NULL DEFAULT 'ACTIVE'::character varying
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid

**Constraints**
- PK PRIMARY KEY (payment_categories_id)
- UNIQUE `payment_categories_code_key` UNIQUE (code)
- UNIQUE `payment_categories_uuid_key` UNIQUE (uuid)
- CHECK `payment_categories_direction_check` CHECK (((direction)::text = ANY ((ARRAY['IN'::character varying, 'OUT'::character varying])::text[])))

**Indexes**
- `idx_payment_categories_direction` btree (direction) WHERE is_active

**Triggers**
- `trg_payment_categories_updated_at` BEFORE UPDATE ON app.payment_categories FOR EACH ROW EXECUTE FUNCTION set_updated_at()

**Referenced by** `payments`.(payment_type_id)

## payment_credit_claims

> Reclamo sobre el saldo restante de un pago aprobado (error, pago de más, reembolso); congela ese monto de la aplicación automática.

**Columns**
- `payment_credit_claims_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `payment_id` bigint NOT NULL
- `reason` character varying(20) NOT NULL
- `claimed_amount` numeric(12,2) NOT NULL
- `comment` character varying(500)
- `claim_status` character varying(20) NOT NULL DEFAULT 'OPEN'::character varying
- `opened_by` bigint
- `opened_at` timestamp with time zone NOT NULL DEFAULT now()
- `resolved_by` bigint
- `resolved_at` timestamp with time zone
- `resolution_note` character varying(500)
- `refunded_amount` numeric(12,2)
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50)
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid

**Constraints**
- PK PRIMARY KEY (payment_credit_claims_id)
- FK `payment_credit_claims_opened_by_fkey` FOREIGN KEY (opened_by) REFERENCES users(users_id)
- FK `payment_credit_claims_payment_id_fkey` FOREIGN KEY (payment_id) REFERENCES payments(payments_id)
- FK `payment_credit_claims_resolved_by_fkey` FOREIGN KEY (resolved_by) REFERENCES users(users_id)
- UNIQUE `payment_credit_claims_uuid_key` UNIQUE (uuid)
- CHECK `chk_payment_credit_claims_amount` CHECK ((claimed_amount > (0)::numeric))
- CHECK `chk_payment_credit_claims_reason` CHECK (((reason)::text = ANY ((ARRAY['ERROR'::character varying, 'OVERPAID'::character varying, 'REFUND_REQUEST'::character varying, 'OTHER'::character varying])::text[])))
- CHECK `chk_payment_credit_claims_status` CHECK (((claim_status)::text = ANY ((ARRAY['OPEN'::character varying, 'KEPT_AS_CREDIT'::character varying, 'REFUNDED'::character varying, 'REJECTED'::character varying, 'WITHDRAWN'::character varying, 'USED_BY_AGREEMENT'::character varying])::text[])))

**Indexes**
- `idx_payment_credit_claims_status` btree (claim_status, opened_at DESC)
- `uq_payment_credit_claims_one_open` UNIQUE btree (payment_id) WHERE ((claim_status)::text = 'OPEN'::text)

**Triggers**
- `trg_payment_credit_claims_updated_at` BEFORE UPDATE ON app.payment_credit_claims FOR EACH ROW EXECUTE FUNCTION set_updated_at()

## payment_lines

**Columns**
- `payment_line_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `payment_id` bigint NOT NULL
- `payment_type_id` bigint NOT NULL
- `bank_id` bigint
- `amount` numeric(10,2) NOT NULL
- `currency_id` bigint NOT NULL
- `reference_number` character varying(80)
- `status` character varying(50) NOT NULL DEFAULT 'PENDING'::character varying
- `reviewed_by` bigint
- `reviewed_at` timestamp with time zone
- `review_reason` text
- `is_active` boolean NOT NULL DEFAULT true
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid
- `identification` character varying(80)
- `bank_account_type` character varying(40)
- `bank_account_code` character varying(40)
- `bank_account_identifier` character varying(120)
- `phone` character varying(40)
- `email` character varying(160)
- `company_bank_account_id` bigint — Cuenta de la empresa en la que el pagador indica haber pagado (opcional).
- `payout_account_id` bigint — Cuenta de cobro del asesor a la que se pagó (pagos de salida); la cuenta de origen es company_bank_account_id.

**Constraints**
- PK PRIMARY KEY (payment_line_id)
- FK `payment_lines_bank_id_fkey` FOREIGN KEY (bank_id) REFERENCES banks(banks_id)
- FK `payment_lines_company_bank_account_id_fkey` FOREIGN KEY (company_bank_account_id) REFERENCES company_bank_accounts(company_bank_accounts_id)
- FK `payment_lines_currency_id_fkey` FOREIGN KEY (currency_id) REFERENCES currencies(currencies_id)
- FK `payment_lines_payment_id_fkey` FOREIGN KEY (payment_id) REFERENCES payments(payments_id) ON DELETE CASCADE
- FK `payment_lines_payment_type_id_fkey` FOREIGN KEY (payment_type_id) REFERENCES payment_methods(payment_methods_id)
- FK `payment_lines_payout_account_id_fkey` FOREIGN KEY (payout_account_id) REFERENCES promoter_payout_accounts(promoter_payout_accounts_id)
- FK `payment_lines_reviewed_by_fkey` FOREIGN KEY (reviewed_by) REFERENCES users(users_id)
- UNIQUE `payment_lines_uuid_key` UNIQUE (uuid)
- CHECK `chk_payment_lines_rejection_has_reason` CHECK ((((status)::text <> 'REJECTED'::text) OR (review_reason IS NOT NULL)))
- CHECK `chk_payment_lines_review_consistency` CHECK (((((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'PENDING'::character varying])::text[])) AND (reviewed_at IS NULL) AND (reviewed_by IS NULL)) OR (((status)::text = ANY ((ARRAY['APPROVED'::character varying, 'REJECTED'::character varying])::text[])) AND (reviewed_at IS NOT NULL) AND (reviewed_by IS NOT NULL))))
- CHECK `payment_lines_amount_check` CHECK ((amount > (0)::numeric))
- CHECK `payment_lines_status_check` CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying])::text[])))

**Indexes**
- `idx_payment_lines_payment` btree (payment_id)
- `idx_payment_lines_status` btree (status) WHERE is_active

**Triggers**
- `trg_payment_lines_updated_at` BEFORE UPDATE ON app.payment_lines FOR EACH ROW EXECUTE FUNCTION set_updated_at()

## payment_methods

**Columns**
- `payment_methods_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `code` character varying(40) NOT NULL
- `name` character varying(80) NOT NULL
- `is_mandatory_bank_account` boolean NOT NULL DEFAULT false
- `is_mandatory_phone` boolean NOT NULL DEFAULT false
- `is_mandatory_email` boolean NOT NULL DEFAULT false
- `is_mandatory_reference_number` boolean NOT NULL DEFAULT true
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50) NOT NULL DEFAULT 'ACTIVE'::character varying
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid
- `description` character varying(255)
- `is_mandatory_identification` boolean NOT NULL DEFAULT false
- `is_mandatory_account_type` boolean NOT NULL DEFAULT false
- `is_mandatory_account_code` boolean NOT NULL DEFAULT false
- `is_mandatory_bank` boolean NOT NULL DEFAULT false
- `requires_approval` boolean NOT NULL DEFAULT true

**Constraints**
- PK PRIMARY KEY (payment_methods_id)
- UNIQUE `payment_methods_code_key` UNIQUE (code)
- UNIQUE `payment_methods_uuid_key` UNIQUE (uuid)

**Triggers**
- `trg_payment_methods_updated_at` BEFORE UPDATE ON app.payment_methods FOR EACH ROW EXECUTE FUNCTION set_updated_at()

**Referenced by** `affiliation_requests`.(payment_method_id), `company_bank_account_payment_methods`.(payment_method_id), `payer_bank_account_payment_methods`.(payment_method_id), `payment_lines`.(payment_type_id), `promoter_payout_account_payment_methods`.(payment_method_id)

## payments

**Columns**
- `payments_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `membership_id` bigint
- `payer_user_id` bigint
- `amount` numeric(10,2) NOT NULL
- `payment_date` timestamp with time zone NOT NULL
- `received_at` timestamp with time zone NOT NULL DEFAULT now()
- `inscription` boolean NOT NULL DEFAULT false
- `applied_period` date
- `support_file_url` character varying(500)
- `support_file_name` character varying(255)
- `support_file_content_type` character varying(100)
- `support_file_size_bytes` bigint
- `admin_notes` text
- `reviewed_by` bigint
- `reviewed_at` timestamp with time zone
- `review_reason` text
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50) NOT NULL DEFAULT 'PENDING'::character varying
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid
- `corporate_contract_id` bigint
- `discount_amount` numeric(10,2)
- `discount_reason` text
- `discounted_by` bigint
- `discounted_at` timestamp with time zone
- `currency_id` bigint NOT NULL
- `exchange_rate_used` numeric(18,8)
- `exchange_rate_date` date
- `direction` character varying(10) NOT NULL
- `payment_type_id` bigint NOT NULL
- `person_id` bigint NOT NULL
- `promoter_id` bigint
- `campaign_id` bigint
- `coverage_through_period` date
- `beneficiary_person_id` bigint — Persona a quien se acredita el pago cuando quien paga no es el titular; NULL = se acredita a quien paga.
- `beneficiary_document` character varying(30) — Documento del beneficiario al momento del pago (referencia para cotejar con el banco).
- `beneficiary_contract_number` character varying(20) — Número de contrato de la membresía indicada al momento del pago.

**Constraints**
- PK PRIMARY KEY (payments_id)
- FK `payments_beneficiary_person_id_fkey` FOREIGN KEY (beneficiary_person_id) REFERENCES persons(persons_id)
- FK `payments_campaign_id_fkey` FOREIGN KEY (campaign_id) REFERENCES campaigns(campaigns_id)
- FK `payments_corporate_contract_id_fkey` FOREIGN KEY (corporate_contract_id) REFERENCES corporate_contracts(corporate_contracts_id)
- FK `payments_currency_id_fkey` FOREIGN KEY (currency_id) REFERENCES currencies(currencies_id)
- FK `payments_discounted_by_fkey` FOREIGN KEY (discounted_by) REFERENCES users(users_id)
- FK `payments_membership_id_fkey` FOREIGN KEY (membership_id) REFERENCES memberships(memberships_id)
- FK `payments_payer_user_id_fkey` FOREIGN KEY (payer_user_id) REFERENCES users(users_id)
- FK `payments_payment_type_id_fkey` FOREIGN KEY (payment_type_id) REFERENCES payment_categories(payment_categories_id)
- FK `payments_person_id_fkey` FOREIGN KEY (person_id) REFERENCES persons(persons_id)
- FK `payments_promoter_id_fkey` FOREIGN KEY (promoter_id) REFERENCES promoters(promoters_id)
- FK `payments_reviewed_by_fkey` FOREIGN KEY (reviewed_by) REFERENCES users(users_id)
- UNIQUE `payments_uuid_key` UNIQUE (uuid)
- CHECK `chk_payments_date_not_future` CHECK ((payment_date <= (CURRENT_TIMESTAMP + '1 day'::interval)))
- CHECK `chk_payments_direction` CHECK (((direction)::text = ANY ((ARRAY['IN'::character varying, 'OUT'::character varying])::text[])))
- CHECK `chk_payments_discount_amount` CHECK (((discount_amount IS NULL) OR (discount_amount >= (0)::numeric)))
- CHECK `chk_payments_discount_coherence` CHECK (((discount_amount IS NULL) OR ((discount_reason IS NOT NULL) AND (discounted_at IS NOT NULL))))
- CHECK `chk_payments_discount_not_exceed` CHECK (((discount_amount IS NULL) OR (discount_amount <= amount)))
- CHECK `chk_payments_exchange_rate_paired` CHECK ((((exchange_rate_used IS NULL) AND (exchange_rate_date IS NULL)) OR ((exchange_rate_used IS NOT NULL) AND (exchange_rate_date IS NOT NULL))))
- CHECK `chk_payments_inscription_no_period` CHECK (((NOT inscription) OR (applied_period IS NULL)))
- CHECK `chk_payments_membership_by_direction` CHECK ((((direction)::text = 'IN'::text) OR (((direction)::text = 'OUT'::text) AND (membership_id IS NULL))))
- CHECK `chk_payments_rejection_has_reason` CHECK ((((status)::text <> 'REJECTED'::text) OR (review_reason IS NOT NULL)))
- CHECK `chk_payments_review_consistency` CHECK (((((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'PENDING'::character varying])::text[])) AND (reviewed_at IS NULL) AND (reviewed_by IS NULL)) OR (((status)::text = ANY ((ARRAY['APPROVED'::character varying, 'REJECTED'::character varying])::text[])) AND (reviewed_at IS NOT NULL) AND (reviewed_by IS NOT NULL))))
- CHECK `payments_amount_check` CHECK ((amount > (0)::numeric))
- CHECK `payments_exchange_rate_used_check` CHECK (((exchange_rate_used IS NULL) OR (exchange_rate_used > (0)::numeric)))
- CHECK `payments_status_check` CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying])::text[])))

**Indexes**
- `idx_payments_active_created` btree (created_at DESC) WHERE is_active
- `idx_payments_approved_in_payment_date` btree (payment_date) WHERE (((status)::text = 'APPROVED'::text) AND ((direction)::text = 'IN'::text))
- `idx_payments_approved_in_received_at` btree (received_at) WHERE (((status)::text = 'APPROVED'::text) AND ((direction)::text = 'IN'::text))
- `idx_payments_approved_in_reviewed_at` btree (reviewed_at) WHERE (((status)::text = 'APPROVED'::text) AND ((direction)::text = 'IN'::text))
- `idx_payments_approved_period` btree (applied_period) WHERE (is_active AND ((status)::text = 'APPROVED'::text))
- `idx_payments_beneficiary_person` btree (beneficiary_person_id) WHERE (beneficiary_person_id IS NOT NULL)
- `idx_payments_campaign` btree (campaign_id) WHERE (campaign_id IS NOT NULL)
- `idx_payments_corporate_contract` btree (corporate_contract_id) WHERE (corporate_contract_id IS NOT NULL)
- `idx_payments_currency` btree (currency_id)
- `idx_payments_direction_status` btree (direction, status)
- `idx_payments_in_event_at` btree (COALESCE(reviewed_at, received_at) DESC) WHERE (is_active AND ((direction)::text = 'IN'::text))
- `idx_payments_membership_date` btree (membership_id, payment_date DESC)
- `idx_payments_payment_type` btree (payment_type_id)
- `idx_payments_person` btree (person_id)
- `idx_payments_promoter_payment_date` btree (promoter_id, payment_date DESC)
- `idx_payments_status_received` btree (status, received_at DESC) WHERE is_active

**Triggers**
- `trg_payments_updated_at` BEFORE UPDATE ON app.payments FOR EACH ROW EXECUTE FUNCTION set_updated_at()

**Referenced by** `affiliation_requests`.(payment_id), `beneficiaries`.(inscription_payment_id), `campaign_transaction_exceptions`.(payment_id), `campaign_transaction_links`.(payment_id), `commission_retroactive_topup_cuts`.(payout_payment_id), `commission_retroactive_topups`.(payout_payment_id), `commissions`.(payment_id), `commissions`.(payout_payment_id), `competitive_commission_award_settlements`.(payout_payment_id), `competitive_commission_awards`.(payout_payment_id), `membership_charges`.(covered_by_payment_id), `payment_credit_claims`.(payment_id), `payment_lines`.(payment_id), `person_credit_entries`.(payment_id), `promoter_hierarchy_overrides`.(payout_payment_id), `referrals`.(reward_payment_id)

## permission_domains

**Columns**
- `permission_domains_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `code` character varying(50) NOT NULL
- `name` character varying(100) NOT NULL
- `icon` character varying(60)
- `description` character varying(255)
- `display_order` integer NOT NULL DEFAULT 100
- `is_active` boolean NOT NULL DEFAULT true
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid

**Constraints**
- PK PRIMARY KEY (permission_domains_id)
- UNIQUE `permission_domains_code_key` UNIQUE (code)
- UNIQUE `permission_domains_name_key` UNIQUE (name)
- UNIQUE `permission_domains_uuid_key` UNIQUE (uuid)

**Indexes**
- `idx_permission_domains_display_order` btree (display_order) WHERE is_active
- `idx_permission_domains_is_active` btree (is_active) WHERE is_active

**Triggers**
- `trg_permission_domains_updated_at` BEFORE UPDATE ON app.permission_domains FOR EACH ROW EXECUTE FUNCTION set_updated_at()

**Referenced by** `permissions`.(domain_id)

## permissions

**Columns**
- `permissions_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `name` character varying(100) NOT NULL
- `domain_id` bigint NOT NULL
- `description` character varying(200)
- `is_active` boolean NOT NULL DEFAULT true
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid

**Constraints**
- PK PRIMARY KEY (permissions_id)
- FK `permissions_domain_id_fkey` FOREIGN KEY (domain_id) REFERENCES permission_domains(permission_domains_id)
- UNIQUE `permissions_name_key` UNIQUE (name)
- UNIQUE `permissions_uuid_key` UNIQUE (uuid)

**Indexes**
- `idx_permissions_domain_id` btree (domain_id)
- `idx_permissions_is_active` btree (is_active) WHERE is_active

**Triggers**
- `trg_permissions_grant_system` AFTER INSERT ON app.permissions FOR EACH ROW EXECUTE FUNCTION grant_permission_to_system()
- `trg_permissions_updated_at` BEFORE UPDATE ON app.permissions FOR EACH ROW EXECUTE FUNCTION set_updated_at()

**Referenced by** `role_permissions`.(permission_id)

## person_credit_entries

> Libro de saldo a favor por persona y moneda: CREDIT (pago aprobado sin membresía) / APPLIED (asignado a una membresía), importe con signo.

**Columns**
- `person_credit_entries_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `person_id` bigint NOT NULL
- `payment_id` bigint NOT NULL
- `membership_id` bigint
- `currency_id` bigint NOT NULL
- `entry_type` character varying(20) NOT NULL
- `amount` numeric(12,2) NOT NULL
- `note` character varying(255)
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50)
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid
- `charge_id` bigint
- `converted_amount` numeric(12,2)
- `converted_currency_id` bigint
- `exchange_rate_used` numeric(18,8)
- `exchange_rate_date` date

**Constraints**
- PK PRIMARY KEY (person_credit_entries_id)
- FK `person_credit_entries_charge_id_fkey` FOREIGN KEY (charge_id) REFERENCES membership_charges(membership_charges_id)
- FK `person_credit_entries_converted_currency_id_fkey` FOREIGN KEY (converted_currency_id) REFERENCES currencies(currencies_id)
- FK `person_credit_entries_currency_id_fkey` FOREIGN KEY (currency_id) REFERENCES currencies(currencies_id)
- FK `person_credit_entries_membership_id_fkey` FOREIGN KEY (membership_id) REFERENCES memberships(memberships_id)
- FK `person_credit_entries_payment_id_fkey` FOREIGN KEY (payment_id) REFERENCES payments(payments_id)
- FK `person_credit_entries_person_id_fkey` FOREIGN KEY (person_id) REFERENCES persons(persons_id)
- UNIQUE `person_credit_entries_uuid_key` UNIQUE (uuid)
- CHECK `chk_person_credit_entries_sign` CHECK (((((entry_type)::text = 'CREDIT'::text) AND (amount > (0)::numeric)) OR (((entry_type)::text <> 'CREDIT'::text) AND (amount < (0)::numeric))))
- CHECK `chk_person_credit_entries_type` CHECK (((entry_type)::text = ANY ((ARRAY['CREDIT'::character varying, 'APPLIED'::character varying, 'REVERSED'::character varying])::text[])))

**Indexes**
- `idx_person_credit_entries_charge` btree (charge_id) WHERE (charge_id IS NOT NULL)
- `idx_person_credit_entries_payment` btree (payment_id)
- `idx_person_credit_entries_person` btree (person_id, currency_id) WHERE is_active

**Triggers**
- `trg_person_credit_entries_updated_at` BEFORE UPDATE ON app.person_credit_entries FOR EACH ROW EXECUTE FUNCTION set_updated_at()

## person_professional_licenses

**Columns**
- `person_professional_licenses_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `person_id` bigint NOT NULL
- `licensing_body_id` bigint NOT NULL
- `license_number` character varying(40) NOT NULL
- `verification_status` character varying(20) NOT NULL DEFAULT 'PENDING'::character varying
- `verified_by` uuid
- `verified_at` timestamp with time zone
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50) NOT NULL DEFAULT 'ACTIVE'::character varying
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid

**Constraints**
- PK PRIMARY KEY (person_professional_licenses_id)
- FK `person_professional_licenses_licensing_body_id_fkey` FOREIGN KEY (licensing_body_id) REFERENCES licensing_bodies(licensing_bodies_id)
- FK `person_professional_licenses_person_id_fkey` FOREIGN KEY (person_id) REFERENCES persons(persons_id)
- UNIQUE `person_professional_licenses_uuid_key` UNIQUE (uuid)
- CHECK `ck_person_professional_licenses_status` CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying])::text[])))
- CHECK `ck_person_professional_licenses_verification` CHECK (((verification_status)::text = ANY ((ARRAY['PENDING'::character varying, 'VERIFIED'::character varying, 'REJECTED'::character varying])::text[])))
- CHECK `ck_person_professional_licenses_verified_stamp` CHECK (((((verification_status)::text = 'PENDING'::text) AND (verified_at IS NULL)) OR (((verification_status)::text <> 'PENDING'::text) AND (verified_at IS NOT NULL))))

**Indexes**
- `idx_person_professional_licenses_person` btree (person_id)
- `uniq_person_professional_licenses_body_number` UNIQUE btree (licensing_body_id, license_number) WHERE is_active

## persons

**Columns**
- `persons_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `first_name` character varying(50) NOT NULL
- `middle_name` character varying(50)
- `last_name` character varying(50) NOT NULL
- `second_last_name` character varying(50)
- `full_name` character varying(210) GENERATED AS (TRIM(BOTH FROM regexp_replace((((((((COALESCE(first_name, ''::character varying))::text || ' '::text) || (COALESCE(middle_name, ''::character varying))::text) || ' '::text) || (COALESCE(last_name, ''::character varying))::text) || ' '::text) || (COALESCE(second_last_name, ''::character varying))::text), '\s+'::text, ' '::text, 'g'::text)))
- `document_type` character varying(2) NOT NULL
- `document_number` character varying(20) NOT NULL
- `tax_document_type` character varying(1)
- `tax_document_number` character varying(20)
- `birth_date` date
- `gender_id` bigint
- `marital_status_id` bigint
- `phone` character varying(30)
- `email` citext
- `locale` character varying(10)
- `address` text
- `city_id` bigint
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50)
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid
- `birthplace` character varying(120)
- `number_of_children` integer
- `landline_phone` character varying(30)
- `spouse_name` character varying(210)
- `credit_auto_apply` boolean NOT NULL DEFAULT true — FALSE = el saldo a favor de la persona no se aplica automáticamente (reclamo, reembolso en curso); lo controla el personal.
- `credit_hold_reason` character varying(255)
- `credit_hold_by` uuid
- `credit_hold_at` timestamp with time zone

**Constraints**
- PK PRIMARY KEY (persons_id)
- FK `persons_city_id_fkey` FOREIGN KEY (city_id) REFERENCES cities(cities_id)
- FK `persons_gender_id_fkey` FOREIGN KEY (gender_id) REFERENCES genders(genders_id)
- FK `persons_marital_status_id_fkey` FOREIGN KEY (marital_status_id) REFERENCES marital_statuses(marital_statuses_id)
- UNIQUE `persons_document_type_document_number_key` UNIQUE (document_type, document_number)
- UNIQUE `persons_uuid_key` UNIQUE (uuid)
- CHECK `chk_persons_number_of_children` CHECK (((number_of_children IS NULL) OR (number_of_children >= 0)))
- CHECK `chk_persons_tax_document` CHECK ((((tax_document_type IS NULL) AND (tax_document_number IS NULL)) OR ((tax_document_type IS NOT NULL) AND (tax_document_number IS NOT NULL))))
- CHECK `persons_document_type_check` CHECK (((document_type)::text = ANY ((ARRAY['V'::character varying, 'E'::character varying])::text[])))
- CHECK `persons_tax_document_type_check` CHECK (((tax_document_type)::text = ANY ((ARRAY['J'::character varying, 'V'::character varying, 'E'::character varying, 'G'::character varying, 'P'::character varying])::text[])))

**Indexes**
- `idx_persons_city_id` btree (city_id) WHERE (city_id IS NOT NULL)
- `idx_persons_document_number_trgm` gin (immutable_unaccent(lower((document_number)::text)) gin_trgm_ops)
- `idx_persons_email` btree (email) WHERE (email IS NOT NULL)
- `idx_persons_email_trgm` gin (immutable_unaccent(lower((email)::text)) gin_trgm_ops)
- `idx_persons_full_name_unaccent` gin (immutable_unaccent(lower((full_name)::text)) gin_trgm_ops)
- `idx_persons_last_name` btree (last_name)
- `idx_persons_phone_trgm` gin (immutable_unaccent(lower((phone)::text)) gin_trgm_ops)
- `uniq_persons_tax_document` UNIQUE btree (tax_document_type, tax_document_number) WHERE ((tax_document_type IS NOT NULL) AND (tax_document_number IS NOT NULL))

**Triggers**
- `trg_persons_updated_at` BEFORE UPDATE ON app.persons FOR EACH ROW EXECUTE FUNCTION set_updated_at()

**Referenced by** `allies`.(person_id), `ally_patient_links`.(doctor_person_id), `beneficiaries`.(person_id), `medical_records`.(person_id), `members`.(person_id), `payer_bank_accounts`.(person_id), `payments`.(beneficiary_person_id), `payments`.(person_id), `person_credit_entries`.(person_id), `person_professional_licenses`.(person_id), `promoters`.(person_id), `users`.(person_id)

## plans

**Columns**
- `plans_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `code` character varying(40) NOT NULL
- `name` character varying(100) NOT NULL
- `description` text
- `type` character varying(20) NOT NULL
- `inscription_fee` numeric(10,2) NOT NULL
- `monthly_fee` numeric(10,2) NOT NULL
- `included_beneficiaries` integer NOT NULL DEFAULT 0
- `max_beneficiaries` integer
- `extra_beneficiary_inscription_fee` numeric(10,2)
- `grace_period_days` integer NOT NULL DEFAULT 7
- `is_published` boolean NOT NULL DEFAULT false
- `published_at` timestamp with time zone
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50)
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid
- `currency_id` bigint NOT NULL
- `min_members` integer — Minimum number of people the plan requires (e.g. 20 for CORPORATIVO); NULL = no minimum
- `reinscription_required` boolean NOT NULL DEFAULT false — El plan exige reinscripción en cada vigencia; sin esto la membresía queda abierta (sin vencimiento).
- `term_mode` character varying(20) — FIXED_DATE (fecha anual fija) o PERIOD (N meses desde la afiliación).
- `term_fixed_month` integer
- `term_fixed_day` integer
- `term_period_months` integer

**Constraints**
- PK PRIMARY KEY (plans_id)
- FK `plans_currency_id_fkey` FOREIGN KEY (currency_id) REFERENCES currencies(currencies_id)
- UNIQUE `plans_code_key` UNIQUE (code)
- UNIQUE `plans_uuid_key` UNIQUE (uuid)
- CHECK `chk_plans_term_coherence` CHECK ((((reinscription_required = false) AND (term_mode IS NULL) AND (term_fixed_month IS NULL) AND (term_fixed_day IS NULL) AND (term_period_months IS NULL)) OR ((reinscription_required = true) AND ((term_mode)::text = 'FIXED_DATE'::text) AND (term_fixed_month IS NOT NULL) AND (term_fixed_day IS NOT NULL) AND (term_period_months IS NULL)) OR ((reinscription_required = true) AND ((term_mode)::text = 'PERIOD'::text) AND (term_period_months IS NOT NULL) AND (term_fixed_month IS NULL) AND (term_fixed_day IS NULL))))
- CHECK `chk_plans_term_day` CHECK (((term_fixed_day IS NULL) OR ((term_fixed_day >= 1) AND (term_fixed_day <= 28))))
- CHECK `chk_plans_term_mode` CHECK (((term_mode IS NULL) OR ((term_mode)::text = ANY ((ARRAY['FIXED_DATE'::character varying, 'PERIOD'::character varying])::text[]))))
- CHECK `chk_plans_term_month` CHECK (((term_fixed_month IS NULL) OR ((term_fixed_month >= 1) AND (term_fixed_month <= 12))))
- CHECK `chk_plans_term_period` CHECK (((term_period_months IS NULL) OR (term_period_months > 0)))
- CHECK `ck_plans_min_members` CHECK (((min_members IS NULL) OR (min_members >= 1)))
- CHECK `plans_check` CHECK (((max_beneficiaries IS NULL) OR (max_beneficiaries >= included_beneficiaries)))
- CHECK `plans_extra_beneficiary_inscription_fee_check` CHECK (((extra_beneficiary_inscription_fee IS NULL) OR (extra_beneficiary_inscription_fee >= (0)::numeric)))
- CHECK `plans_grace_period_days_check` CHECK ((grace_period_days >= 0))
- CHECK `plans_included_beneficiaries_check` CHECK ((included_beneficiaries >= 0))
- CHECK `plans_inscription_fee_check` CHECK ((inscription_fee >= (0)::numeric))
- CHECK `plans_monthly_fee_check` CHECK ((monthly_fee >= (0)::numeric))
- CHECK `plans_type_check` CHECK (((type)::text = ANY ((ARRAY['INDIVIDUAL'::character varying, 'FAMILIAR'::character varying, 'CORPORATIVO'::character varying])::text[])))

**Indexes**
- `idx_plans_currency` btree (currency_id)
- `idx_plans_published` btree (is_active) WHERE (is_active AND is_published)
- `idx_plans_type` btree (type)

**Triggers**
- `trg_plans_updated_at` BEFORE UPDATE ON app.plans FOR EACH ROW EXECUTE FUNCTION set_updated_at()

**Referenced by** `affiliation_requests`.(plan_id), `corporate_contracts`.(plan_id), `memberships`.(plan_id), `promotion_plans`.(plan_id)

## product_categories

> Clasificación de productos y servicios ofrecidos por aliados (antes service_categories, ADR 0026).

**Columns**
- `product_categories_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `code` character varying(40) NOT NULL
- `name` character varying(100) NOT NULL
- `description` character varying(200)
- `is_active` boolean NOT NULL DEFAULT true
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid

**Constraints**
- PK PRIMARY KEY (product_categories_id)
- UNIQUE `product_categories_code_key` UNIQUE (code)
- UNIQUE `product_categories_name_key` UNIQUE (name)
- UNIQUE `product_categories_uuid_key` UNIQUE (uuid)

**Indexes**
- `idx_product_categories_is_active` btree (is_active) WHERE is_active

**Triggers**
- `trg_product_categories_updated_at` BEFORE UPDATE ON app.product_categories FOR EACH ROW EXECUTE FUNCTION set_updated_at()

**Referenced by** `benefit_usages`.(product_category_id), `products`.(product_category_id)

## products

> Catálogo maestro de productos y servicios (ADR 0026). owner_ally_id NULL = general; con valor = exclusivo de ese aliado.

**Columns**
- `products_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `code` character varying(40)
- `name` character varying(200) NOT NULL
- `description` text
- `product_type` character varying(20) NOT NULL DEFAULT 'SERVICE'::character varying — SERVICE (consulta, examen, hospedaje…) o PRODUCT (lente, medicamento, repuesto…).
- `product_category_id` bigint NOT NULL
- `owner_ally_id` bigint
- `requires_appointment` boolean NOT NULL DEFAULT false
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50)
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid
- `is_published` boolean NOT NULL DEFAULT true — Aparece en el catálogo general y en la comparativa entre aliados. Sin publicar, se ve solo desde cada aliado que publica su oferta (ADR 0026).
- `published_at` timestamp with time zone
- `logo_url` character varying(500) — URL de la imagen principal (media_items), sincronizada por el servicio de media; no editar a mano (ADR 0027).
- `rating_avg` numeric(3,2) — Promedio de product_rating en todas las ofertas del producto; NULL sin votos.
- `rating_count` integer NOT NULL DEFAULT 0

**Constraints**
- PK PRIMARY KEY (products_id)
- FK `products_owner_ally_id_fkey` FOREIGN KEY (owner_ally_id) REFERENCES allies(allies_id) ON DELETE CASCADE
- FK `products_product_category_id_fkey` FOREIGN KEY (product_category_id) REFERENCES product_categories(product_categories_id)
- UNIQUE `products_code_key` UNIQUE (code)
- UNIQUE `products_uuid_key` UNIQUE (uuid)
- CHECK `chk_products_type` CHECK (((product_type)::text = ANY ((ARRAY['SERVICE'::character varying, 'PRODUCT'::character varying])::text[])))

**Indexes**
- `idx_products_category` btree (product_category_id)
- `idx_products_name_trgm` gin (immutable_unaccent(lower((name)::text)) gin_trgm_ops)
- `idx_products_owner_ally` btree (owner_ally_id) WHERE (owner_ally_id IS NOT NULL)
- `idx_products_published` btree (products_id) WHERE (is_published AND is_active)
- `idx_products_type` btree (product_type)

**Triggers**
- `trg_products_exclusive_owner` BEFORE UPDATE OF owner_ally_id ON app.products FOR EACH ROW EXECUTE FUNCTION enforce_exclusive_product_owner()
- `trg_products_updated_at` BEFORE UPDATE ON app.products FOR EACH ROW EXECUTE FUNCTION set_updated_at()

**Referenced by** `ally_products`.(product_id)

## professions

**Columns**
- `professions_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `code` character varying(40) NOT NULL
- `name` character varying(100) NOT NULL
- `description` character varying(200)
- `is_active` boolean NOT NULL DEFAULT true
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid
- `parent_id` bigint — Profesión general de una sub-especialidad (máximo dos niveles)

**Constraints**
- PK PRIMARY KEY (professions_id)
- FK `professions_parent_id_fkey` FOREIGN KEY (parent_id) REFERENCES professions(professions_id)
- UNIQUE `medical_specialties_code_key` UNIQUE (code)
- UNIQUE `medical_specialties_name_key` UNIQUE (name)
- UNIQUE `medical_specialties_uuid_key` UNIQUE (uuid)
- CHECK `ck_professions_not_own_parent` CHECK (((parent_id IS NULL) OR (parent_id <> professions_id)))

**Indexes**
- `idx_professions_is_active` btree (is_active) WHERE is_active
- `idx_professions_parent` btree (parent_id)

**Triggers**
- `trg_professions_updated_at` BEFORE UPDATE ON app.professions FOR EACH ROW EXECUTE FUNCTION set_updated_at()

**Referenced by** `ally_professions`.(profession_id), `professions`.(parent_id)

## promoter_bonus_awards

**Columns**
- `promoter_bonus_awards_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL
- `bonus_rule_id` bigint NOT NULL
- `promoter_id` bigint NOT NULL
- `window_start` date NOT NULL
- `window_end` date NOT NULL
- `blocks_awarded` integer NOT NULL
- `metric_count` integer NOT NULL
- `reward_type` character varying(20) NOT NULL
- `flat_amount` numeric(10,2)
- `reward_pct` numeric(5,2)
- `basis_amount` numeric(12,2)
- `amount` numeric(12,2) NOT NULL
- `rule_name_snapshot` character varying(150) NOT NULL
- `evaluated_at` timestamp with time zone NOT NULL DEFAULT now()
- `payout_reference` character varying(120)
- `paid_at` timestamp with time zone
- `voided_at` timestamp with time zone
- `void_reason` text
- `admin_notes` text
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50) NOT NULL DEFAULT 'PENDING'::character varying
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid
- `reward_currency_id` bigint NOT NULL
- `exchange_rate_used` numeric(18,8)
- `exchange_rate_date` date
- `cut_kind` character varying(20) NOT NULL
- `cut_sequence` smallint NOT NULL
- `cut_start` date NOT NULL
- `cut_end` date NOT NULL

**Constraints**
- PK PRIMARY KEY (promoter_bonus_awards_id)
- FK `promoter_bonus_awards_bonus_rule_id_fkey` FOREIGN KEY (bonus_rule_id) REFERENCES commission_bonus_rules(commission_bonus_rules_id)
- FK `promoter_bonus_awards_promoter_id_fkey` FOREIGN KEY (promoter_id) REFERENCES promoters(promoters_id)
- FK `promoter_bonus_awards_reward_currency_id_fkey` FOREIGN KEY (reward_currency_id) REFERENCES currencies(currencies_id)
- UNIQUE `promoter_bonus_awards_uuid_key` UNIQUE (uuid)
- CHECK `chk_bonus_award_amount_positive` CHECK ((amount > (0)::numeric))
- CHECK `chk_bonus_award_blocks_positive` CHECK ((blocks_awarded > 0))
- CHECK `chk_bonus_award_paid` CHECK ((((status)::text <> 'PAID'::text) OR ((paid_at IS NOT NULL) AND (payout_reference IS NOT NULL))))
- CHECK `chk_bonus_award_reward` CHECK (((((reward_type)::text = 'FLAT'::text) AND (flat_amount IS NOT NULL) AND (reward_pct IS NULL)) OR (((reward_type)::text = 'PERCENTAGE'::text) AND (reward_pct IS NOT NULL) AND (flat_amount IS NULL))))
- CHECK `chk_bonus_award_reward_type` CHECK (((reward_type)::text = ANY ((ARRAY['FLAT'::character varying, 'PERCENTAGE'::character varying])::text[])))
- CHECK `chk_bonus_award_status` CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PAID'::character varying, 'VOIDED'::character varying])::text[])))
- CHECK `chk_bonus_award_voided` CHECK ((((status)::text <> 'VOIDED'::text) OR ((voided_at IS NOT NULL) AND (void_reason IS NOT NULL))))
- CHECK `chk_bonus_awards_exchange_rate_paired` CHECK ((((exchange_rate_used IS NULL) AND (exchange_rate_date IS NULL)) OR ((exchange_rate_used IS NOT NULL) AND (exchange_rate_date IS NOT NULL))))
- CHECK `chk_promoter_bonus_awards_cut_kind` CHECK (((cut_kind)::text = ANY ((ARRAY['PARTIAL'::character varying, 'RETROACTIVE'::character varying, 'FINAL'::character varying])::text[])))
- CHECK `promoter_bonus_awards_exchange_rate_used_check` CHECK (((exchange_rate_used IS NULL) OR (exchange_rate_used > (0)::numeric)))

**Indexes**
- `idx_bonus_awards_promoter_created` btree (promoter_id, created_at DESC) WHERE is_active
- `idx_bonus_awards_status_created` btree (status, created_at DESC) WHERE is_active
- `uq_promoter_bonus_awards_cut` UNIQUE btree (bonus_rule_id, promoter_id, window_start, cut_kind, cut_sequence)

**Triggers**
- `trg_promoter_bonus_awards_updated_at` BEFORE UPDATE ON app.promoter_bonus_awards FOR EACH ROW EXECUTE FUNCTION set_updated_at()

## promoter_hierarchy_overrides

**Columns**
- `promoter_hierarchy_overrides_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL
- `promoter_id` bigint NOT NULL
- `source_commission_id` bigint
- `source_override_id` bigint
- `category` character varying(20) NOT NULL
- `basis_amount` numeric(10,2) NOT NULL
- `tier_id` bigint NOT NULL
- `amount` numeric(10,2) NOT NULL
- `currency_id` bigint NOT NULL
- `period_strategy` character varying(20) NOT NULL
- `period_start` date NOT NULL
- `period_end` date NOT NULL
- `earned_at` timestamp with time zone NOT NULL DEFAULT now()
- `voided_at` timestamp with time zone
- `void_reason` text
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50) NOT NULL DEFAULT 'PENDING'::character varying
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid
- `payout_reference` character varying(120)
- `paid_at` timestamp with time zone
- `payout_payment_id` bigint

**Constraints**
- PK PRIMARY KEY (promoter_hierarchy_overrides_id)
- FK `promoter_hierarchy_overrides_currency_id_fkey` FOREIGN KEY (currency_id) REFERENCES currencies(currencies_id)
- FK `promoter_hierarchy_overrides_payout_payment_id_fkey` FOREIGN KEY (payout_payment_id) REFERENCES payments(payments_id)
- FK `promoter_hierarchy_overrides_promoter_id_fkey` FOREIGN KEY (promoter_id) REFERENCES promoters(promoters_id)
- FK `promoter_hierarchy_overrides_source_commission_id_fkey` FOREIGN KEY (source_commission_id) REFERENCES commissions(commissions_id)
- FK `promoter_hierarchy_overrides_source_override_id_fkey` FOREIGN KEY (source_override_id) REFERENCES promoter_hierarchy_overrides(promoter_hierarchy_overrides_id)
- FK `promoter_hierarchy_overrides_tier_id_fkey` FOREIGN KEY (tier_id) REFERENCES hierarchy_override_tiers(hierarchy_override_tiers_id)
- UNIQUE `promoter_hierarchy_overrides_uuid_key` UNIQUE (uuid)
- CHECK `chk_promoter_hierarchy_overrides_category` CHECK (((category)::text = ANY ((ARRAY['INSCRIPTION'::character varying, 'COLLECTION'::character varying])::text[])))
- CHECK `chk_promoter_hierarchy_overrides_source_xor` CHECK (((source_commission_id IS NOT NULL) <> (source_override_id IS NOT NULL)))
- CHECK `chk_promoter_hierarchy_overrides_status` CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PAID'::character varying, 'VOIDED'::character varying])::text[])))

**Indexes**
- `idx_promoter_hierarchy_overrides_payout_payment` btree (payout_payment_id)
- `idx_promoter_hierarchy_overrides_promoter_period` btree (promoter_id, status, period_start, period_end)
- `idx_promoter_hierarchy_overrides_source_commission` btree (source_commission_id)
- `idx_promoter_hierarchy_overrides_source_override` btree (source_override_id)

**Triggers**
- `trg_promoter_hierarchy_overrides_updated_at` BEFORE UPDATE ON app.promoter_hierarchy_overrides FOR EACH ROW EXECUTE FUNCTION set_updated_at()

**Referenced by** `promoter_hierarchy_overrides`.(source_override_id)

## promoter_member_contacts

**Columns**
- `promoter_member_contacts_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL
- `promoter_id` bigint NOT NULL
- `member_id` bigint NOT NULL
- `type` character varying(20) NOT NULL
- `note` text
- `promised_amount` numeric(10,2)
- `promised_at_date` date
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50)
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid
- `promised_currency_id` bigint

**Constraints**
- PK PRIMARY KEY (promoter_member_contacts_id)
- FK `promoter_member_contacts_member_id_fkey` FOREIGN KEY (member_id) REFERENCES members(members_id)
- FK `promoter_member_contacts_promised_currency_id_fkey` FOREIGN KEY (promised_currency_id) REFERENCES currencies(currencies_id)
- FK `promoter_member_contacts_promoter_id_fkey` FOREIGN KEY (promoter_id) REFERENCES promoters(promoters_id)
- UNIQUE `promoter_member_contacts_uuid_key` UNIQUE (uuid)
- CHECK `chk_promoter_member_contacts_promised_currency_paired` CHECK ((((promised_amount IS NULL) AND (promised_currency_id IS NULL)) OR ((promised_amount IS NOT NULL) AND (promised_currency_id IS NOT NULL))))
- CHECK `promoter_member_contacts_amount_positive` CHECK (((promised_amount IS NULL) OR (promised_amount > (0)::numeric)))
- CHECK `promoter_member_contacts_promise_coherence` CHECK (((((type)::text = 'PAYMENT_PROMISE'::text) AND (promised_amount IS NOT NULL) AND (promised_at_date IS NOT NULL)) OR (((type)::text <> 'PAYMENT_PROMISE'::text) AND (promised_amount IS NULL) AND (promised_at_date IS NULL))))
- CHECK `promoter_member_contacts_type_check` CHECK (((type)::text = ANY ((ARRAY['REMINDER'::character varying, 'PAYMENT_PROMISE'::character varying, 'NOTE'::character varying])::text[])))

**Indexes**
- `idx_promoter_member_contacts_promoter_member` btree (promoter_id, member_id, created_at DESC)

## promoter_payout_account_payment_methods

> Métodos de pago que acepta cada cuenta de cobro del asesor; lo que exige la cuenta es la suma de los is_mandatory_* de sus métodos.

**Columns**
- `promoter_payout_account_id` bigint NOT NULL
- `payment_method_id` bigint NOT NULL

**Constraints**
- PK PRIMARY KEY (promoter_payout_account_id, payment_method_id)
- FK `promoter_payout_account_payment_methods_payment_method_id_fkey` FOREIGN KEY (payment_method_id) REFERENCES payment_methods(payment_methods_id)
- FK `promoter_payout_account_payment_promoter_payout_account_id_fkey` FOREIGN KEY (promoter_payout_account_id) REFERENCES promoter_payout_accounts(promoter_payout_accounts_id) ON DELETE CASCADE

**Indexes**
- `idx_payout_account_methods_method` btree (payment_method_id)

## promoter_payout_accounts

> Cuentas de cobro del asesor (expediente, sección V); N por asesor, una predeterminada

**Columns**
- `promoter_payout_accounts_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `promoter_id` bigint NOT NULL
- `label` character varying(60)
- `bank_id` bigint
- `account_type` character varying(20) — Tipo de cuenta bancaria: CORRIENTE o AHORRO
- `account_number` character varying(34)
- `account_identifier` character varying(120) — Identificador libre (cuenta Ubii, cuenta internacional, billetera)
- `holder_name` character varying(150)
- `holder_document_type` character varying(1)
- `holder_document_number` character varying(20)
- `phone` character varying(30)
- `email` character varying(160)
- `is_default` boolean NOT NULL DEFAULT false — Cuenta predeterminada para prellenar pagos de salida (máximo una activa por asesor)
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50) NOT NULL DEFAULT 'ACTIVE'::character varying
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid

**Constraints**
- PK PRIMARY KEY (promoter_payout_accounts_id)
- FK `promoter_payout_accounts_bank_id_fkey` FOREIGN KEY (bank_id) REFERENCES banks(banks_id)
- FK `promoter_payout_accounts_promoter_id_fkey` FOREIGN KEY (promoter_id) REFERENCES promoters(promoters_id) ON DELETE CASCADE
- UNIQUE `promoter_payout_accounts_uuid_key` UNIQUE (uuid)
- CHECK `ck_promoter_payout_accounts_account_type` CHECK (((account_type IS NULL) OR ((account_type)::text = ANY ((ARRAY['CORRIENTE'::character varying, 'AHORRO'::character varying])::text[]))))
- CHECK `ck_promoter_payout_accounts_holder_document_pair` CHECK ((((holder_document_type IS NULL) AND (holder_document_number IS NULL)) OR ((holder_document_type IS NOT NULL) AND (holder_document_number IS NOT NULL))))
- CHECK `ck_promoter_payout_accounts_holder_document_type` CHECK (((holder_document_type IS NULL) OR ((holder_document_type)::text = ANY ((ARRAY['J'::character varying, 'V'::character varying, 'E'::character varying, 'G'::character varying, 'P'::character varying])::text[]))))
- CHECK `ck_promoter_payout_accounts_status` CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying])::text[])))

**Indexes**
- `idx_promoter_payout_accounts_bank` btree (bank_id) WHERE (bank_id IS NOT NULL)
- `idx_promoter_payout_accounts_promoter` btree (promoter_id)
- `uq_promoter_payout_accounts_default` UNIQUE btree (promoter_id) WHERE (is_default AND is_active)

**Triggers**
- `trg_promoter_payout_accounts_updated_at` BEFORE UPDATE ON app.promoter_payout_accounts FOR EACH ROW EXECUTE FUNCTION set_updated_at()

**Referenced by** `payment_lines`.(payout_account_id), `promoter_payout_account_payment_methods`.(promoter_payout_account_id)

## promoter_ranks

**Columns**
- `promoter_ranks_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL
- `code` character varying(40) NOT NULL
- `name` character varying(100) NOT NULL
- `hierarchy_level` integer NOT NULL
- `max_subordinates` integer
- `description` character varying(200)
- `is_active` boolean NOT NULL DEFAULT true
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid
- `parent_rank_id` bigint
- `max_discount_pct` numeric(5,2)

**Constraints**
- PK PRIMARY KEY (promoter_ranks_id)
- FK `promoter_ranks_parent_rank_id_fkey` FOREIGN KEY (parent_rank_id) REFERENCES promoter_ranks(promoter_ranks_id)
- UNIQUE `promoter_ranks_code_key` UNIQUE (code)
- UNIQUE `promoter_ranks_hierarchy_level_key` UNIQUE (hierarchy_level) DEFERRABLE INITIALLY DEFERRED
- UNIQUE `promoter_ranks_name_key` UNIQUE (name)
- UNIQUE `promoter_ranks_uuid_key` UNIQUE (uuid)
- CHECK `chk_promoter_ranks_max_discount_pct` CHECK (((max_discount_pct IS NULL) OR ((max_discount_pct >= (0)::numeric) AND (max_discount_pct <= (100)::numeric))))

**Indexes**
- `idx_promoter_ranks_parent` btree (parent_rank_id)

**Triggers**
- `trg_promoter_ranks_updated_at` BEFORE UPDATE ON app.promoter_ranks FOR EACH ROW EXECUTE FUNCTION set_updated_at()

**Referenced by** `competitive_commission_rule_ranks`.(rank_id), `hierarchy_override_tiers`.(rank_id), `promoter_ranks`.(parent_rank_id), `promoters`.(rank_id)

## promoter_references

> Referencias personales del asesor (expediente, sección IV)

**Columns**
- `promoter_references_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `promoter_id` bigint NOT NULL
- `first_name` character varying(50) NOT NULL
- `last_name` character varying(50) NOT NULL
- `document_type` character varying(1)
- `document_number` character varying(20)
- `phone` character varying(30)
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50) NOT NULL DEFAULT 'ACTIVE'::character varying
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid

**Constraints**
- PK PRIMARY KEY (promoter_references_id)
- FK `promoter_references_promoter_id_fkey` FOREIGN KEY (promoter_id) REFERENCES promoters(promoters_id) ON DELETE CASCADE
- UNIQUE `promoter_references_uuid_key` UNIQUE (uuid)
- CHECK `ck_promoter_references_document_pair` CHECK ((((document_type IS NULL) AND (document_number IS NULL)) OR ((document_type IS NOT NULL) AND (document_number IS NOT NULL))))
- CHECK `ck_promoter_references_document_type` CHECK (((document_type IS NULL) OR ((document_type)::text = ANY ((ARRAY['V'::character varying, 'E'::character varying])::text[]))))
- CHECK `ck_promoter_references_status` CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying])::text[])))

**Indexes**
- `idx_promoter_references_promoter` btree (promoter_id)

**Triggers**
- `trg_promoter_references_updated_at` BEFORE UPDATE ON app.promoter_references FOR EACH ROW EXECUTE FUNCTION set_updated_at()

## promoter_supervisor_assignments

**Columns**
- `promoter_supervisor_assignments_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL
- `promoter_id` bigint NOT NULL
- `from_supervisor_id` bigint
- `to_supervisor_id` bigint
- `actor_user_id` bigint
- `reason` text NOT NULL
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50)
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid

**Constraints**
- PK PRIMARY KEY (promoter_supervisor_assignments_id)
- FK `promoter_supervisor_assignments_actor_user_id_fkey` FOREIGN KEY (actor_user_id) REFERENCES users(users_id)
- FK `promoter_supervisor_assignments_from_supervisor_id_fkey` FOREIGN KEY (from_supervisor_id) REFERENCES promoters(promoters_id)
- FK `promoter_supervisor_assignments_promoter_id_fkey` FOREIGN KEY (promoter_id) REFERENCES promoters(promoters_id)
- FK `promoter_supervisor_assignments_to_supervisor_id_fkey` FOREIGN KEY (to_supervisor_id) REFERENCES promoters(promoters_id)
- UNIQUE `promoter_supervisor_assignments_uuid_key` UNIQUE (uuid)

**Indexes**
- `idx_promoter_supervisor_assignments_promoter` btree (promoter_id, created_at DESC)

## promoter_types

**Columns**
- `promoter_types_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `code` character varying(40) NOT NULL
- `name` character varying(100) NOT NULL
- `description` character varying(200)
- `is_active` boolean NOT NULL DEFAULT true
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid
- `generates_hierarchy_override` boolean NOT NULL DEFAULT true — FALSE = a commission earned by a promoter of this type never cascades a hierarchy override up to their supervisor/coordinador chain (V102). Default TRUE preserves today's behavior for every existing type.
- `max_discount_pct` numeric(5,2)

**Constraints**
- PK PRIMARY KEY (promoter_types_id)
- UNIQUE `promoter_types_code_key` UNIQUE (code)
- UNIQUE `promoter_types_name_key` UNIQUE (name)
- UNIQUE `promoter_types_uuid_key` UNIQUE (uuid)
- CHECK `chk_promoter_types_max_discount_pct` CHECK (((max_discount_pct IS NULL) OR ((max_discount_pct >= (0)::numeric) AND (max_discount_pct <= (100)::numeric))))

**Indexes**
- `idx_promoter_types_is_active` btree (is_active) WHERE is_active

**Triggers**
- `trg_promoter_types_updated_at` BEFORE UPDATE ON app.promoter_types FOR EACH ROW EXECUTE FUNCTION set_updated_at()

**Referenced by** `collection_commission_tier_promoter_types`.(promoter_type_id), `commission_bonus_rule_promoter_types`.(promoter_type_id), `commission_tier_promoter_types`.(promoter_type_id), `competitive_commission_rule_promoter_types`.(promoter_type_id), `promoters`.(promoter_type_id)

## promoters

**Columns**
- `promoters_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `user_id` bigint
- `person_id` bigint
- `display_name` character varying(120) NOT NULL
- `description` text
- `referral_code` character varying(20) NOT NULL
- `is_system` boolean NOT NULL DEFAULT false
- `email` citext
- `phone` character varying(30)
- `total_referrals` integer NOT NULL DEFAULT 0
- `total_commission_paid` numeric(12,2) NOT NULL DEFAULT 0
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50) NOT NULL DEFAULT 'ACTIVE'::character varying
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid
- `promoter_type_id` bigint
- `rank_id` bigint NOT NULL
- `supervisor_id` bigint
- `joined_at` date — Fecha de ingreso del asesor (expediente, sección I)
- `assigned_zone` character varying(120) — Región/zona asignada (texto libre)
- `education_level` character varying(20) — Nivel académico: PRIMARY, SECONDARY, TECHNICAL, UNIVERSITY, POSTGRADUATE
- `profession` character varying(120)
- `years_of_experience` smallint
- `last_company` character varying(150)
- `last_position` character varying(100)
- `vehicle` character varying(120) — Vehículo propio (texto libre: marca/modelo/año, o vacío si no tiene)

**Constraints**
- PK PRIMARY KEY (promoters_id)
- FK `promoters_person_id_fkey` FOREIGN KEY (person_id) REFERENCES persons(persons_id)
- FK `promoters_promoter_type_id_fkey` FOREIGN KEY (promoter_type_id) REFERENCES promoter_types(promoter_types_id)
- FK `promoters_rank_id_fkey` FOREIGN KEY (rank_id) REFERENCES promoter_ranks(promoter_ranks_id)
- FK `promoters_supervisor_id_fkey` FOREIGN KEY (supervisor_id) REFERENCES promoters(promoters_id)
- FK `promoters_user_id_fkey` FOREIGN KEY (user_id) REFERENCES users(users_id)
- UNIQUE `promoters_referral_code_key` UNIQUE (referral_code)
- UNIQUE `promoters_uuid_key` UNIQUE (uuid)
- CHECK `chk_promoters_referral_code_format` CHECK (((referral_code)::text ~ '^[A-Z0-9-]{4,20}$'::text))
- CHECK `chk_promoters_system_or_user` CHECK (((is_system = true) OR ((user_id IS NOT NULL) AND (person_id IS NOT NULL))))
- CHECK `ck_promoters_education_level` CHECK (((education_level IS NULL) OR ((education_level)::text = ANY ((ARRAY['PRIMARY'::character varying, 'SECONDARY'::character varying, 'TECHNICAL'::character varying, 'UNIVERSITY'::character varying, 'POSTGRADUATE'::character varying])::text[]))))
- CHECK `ck_promoters_years_of_experience` CHECK (((years_of_experience IS NULL) OR (years_of_experience >= 0)))
- CHECK `promoters_status_check` CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying, 'SUSPENDED'::character varying])::text[])))
- CHECK `promoters_total_commission_paid_check` CHECK ((total_commission_paid >= (0)::numeric))
- CHECK `promoters_total_referrals_check` CHECK ((total_referrals >= 0))

**Indexes**
- `idx_promoters_active_created` btree (created_at DESC) WHERE is_active
- `idx_promoters_promoter_type_id` btree (promoter_type_id) WHERE (promoter_type_id IS NOT NULL)
- `idx_promoters_rank` btree (rank_id)
- `idx_promoters_real_leaderboard` btree (total_commission_paid DESC) WHERE ((is_active = true) AND (is_system = false))
- `idx_promoters_supervisor` btree (supervisor_id)
- `uniq_promoters_system_row` UNIQUE btree ((true)) WHERE (is_system = true)
- `uniq_promoters_user_id` UNIQUE btree (user_id) WHERE ((user_id IS NOT NULL) AND (is_active = true))

**Triggers**
- `trg_promoters_updated_at` BEFORE UPDATE ON app.promoters FOR EACH ROW EXECUTE FUNCTION set_updated_at()

**Referenced by** `affiliation_requests`.(promoter_id), `allies`.(captured_by_promoter_id), `campaign_promoters`.(promoter_id), `commission_retroactive_topup_cuts`.(promoter_id), `commission_retroactive_topups`.(promoter_id), `commissions`.(promoter_id), `competitive_commission_award_settlements`.(promoter_id), `competitive_commission_awards`.(promoter_id), `competitive_commission_manual_decisions`.(promoter_id), `competitive_commission_manual_decisions`.(replaced_promoter_id), `competitive_commission_tie_candidates`.(promoter_id), `member_promoter_assignments`.(from_promoter_id), `member_promoter_assignments`.(to_promoter_id), `members`.(promoter_id), `membership_promotions`.(code_owner_promoter_id), `payments`.(promoter_id), `promoter_bonus_awards`.(promoter_id), `promoter_hierarchy_overrides`.(promoter_id), `promoter_member_contacts`.(promoter_id), `promoter_payout_accounts`.(promoter_id), `promoter_references`.(promoter_id), `promoter_supervisor_assignments`.(from_supervisor_id), `promoter_supervisor_assignments`.(promoter_id), `promoter_supervisor_assignments`.(to_supervisor_id), `promoters`.(supervisor_id)

## promotion_plans

**Columns**
- `promotion_id` bigint NOT NULL
- `plan_id` bigint NOT NULL

**Constraints**
- PK PRIMARY KEY (promotion_id, plan_id)
- FK `promotion_plans_plan_id_fkey` FOREIGN KEY (plan_id) REFERENCES plans(plans_id)
- FK `promotion_plans_promotion_id_fkey` FOREIGN KEY (promotion_id) REFERENCES promotions(promotions_id) ON DELETE CASCADE

## promotions

**Columns**
- `promotions_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL
- `campaign_id` bigint NOT NULL
- `name` character varying(150) NOT NULL
- `description` text
- `kind` character varying(20) NOT NULL
- `discount_pct` numeric(5,2)
- `applies_to` character varying(20) NOT NULL
- `cycles` integer
- `covers_extra_beneficiaries` boolean NOT NULL DEFAULT false
- `max_redemptions` integer
- `redemptions_count` integer NOT NULL DEFAULT 0
- `requires_code` boolean NOT NULL DEFAULT false
- `accepts_promoter_code` boolean NOT NULL DEFAULT false
- `accepts_member_code` boolean NOT NULL DEFAULT false
- `accepts_ally_code` boolean NOT NULL DEFAULT false
- `referrer_reward_pct` numeric(5,2)
- `referrer_reward_cycles` integer
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50)
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid
- `discount_type` character varying(20) NOT NULL DEFAULT 'PERCENTAGE'::character varying — Tipo de descuento: PERCENTAGE (porcentaje) o FLAT (monto fijo)
- `discount_amount` numeric(18,2) — Monto fijo de descuento por cobro, en discount_currency_id; solo para FLAT
- `discount_currency_id` bigint — Moneda del monto fijo; solo para FLAT

**Constraints**
- PK PRIMARY KEY (promotions_id)
- FK `promotions_campaign_id_fkey` FOREIGN KEY (campaign_id) REFERENCES campaigns(campaigns_id)
- FK `promotions_discount_currency_id_fkey` FOREIGN KEY (discount_currency_id) REFERENCES currencies(currencies_id)
- UNIQUE `promotions_uuid_key` UNIQUE (uuid)
- CHECK `chk_promotions_applies_to` CHECK (((applies_to)::text = ANY ((ARRAY['INSCRIPTION'::character varying, 'MONTHLY'::character varying, 'BOTH'::character varying])::text[])))
- CHECK `chk_promotions_code_owner` CHECK (((NOT requires_code) OR accepts_promoter_code OR accepts_member_code OR accepts_ally_code))
- CHECK `chk_promotions_cycles` CHECK (((cycles IS NULL) OR (cycles >= 1)))
- CHECK `chk_promotions_discount_coherence` CHECK (((((discount_type)::text = 'PERCENTAGE'::text) AND (discount_pct IS NOT NULL) AND (discount_pct > (0)::numeric) AND (discount_pct <= (100)::numeric) AND (discount_amount IS NULL) AND (discount_currency_id IS NULL)) OR (((discount_type)::text = 'FLAT'::text) AND (discount_amount IS NOT NULL) AND (discount_amount > (0)::numeric) AND (discount_currency_id IS NOT NULL) AND (discount_pct IS NULL))))
- CHECK `chk_promotions_discount_type` CHECK (((discount_type)::text = ANY ((ARRAY['PERCENTAGE'::character varying, 'FLAT'::character varying])::text[])))
- CHECK `chk_promotions_kind` CHECK (((kind)::text = ANY ((ARRAY['ACQUISITION'::character varying, 'RECOVERY'::character varying])::text[])))
- CHECK `chk_promotions_max_redemptions` CHECK (((max_redemptions IS NULL) OR (max_redemptions >= 1)))
- CHECK `chk_promotions_recovery_monthly` CHECK ((((kind)::text <> 'RECOVERY'::text) OR ((applies_to)::text = 'MONTHLY'::text)))
- CHECK `chk_promotions_redemptions_count` CHECK ((redemptions_count >= 0))
- CHECK `chk_promotions_referrer_reward` CHECK ((((referrer_reward_pct IS NULL) AND (referrer_reward_cycles IS NULL)) OR ((referrer_reward_pct > (0)::numeric) AND (referrer_reward_pct <= (100)::numeric) AND (referrer_reward_cycles >= 1) AND accepts_member_code)))

**Indexes**
- `idx_promotions_campaign` btree (campaign_id) WHERE is_active

**Triggers**
- `trg_promotions_updated_at` BEFORE UPDATE ON app.promotions FOR EACH ROW EXECUTE FUNCTION set_updated_at()

**Referenced by** `membership_promotions`.(promotion_id), `promotion_plans`.(promotion_id)

## referrals

**Columns**
- `referrals_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `referrer_member_id` bigint NOT NULL
- `referred_member_id` bigint
- `referral_code` character varying(20) NOT NULL
- `enrolled_at` timestamp with time zone
- `expires_at` timestamp with time zone
- `reward_pct` numeric(5,2)
- `reward_flat_amount` numeric(10,2)
- `reward_payment_id` bigint
- `reward_granted_at` timestamp with time zone
- `admin_notes` text
- `void_reason` text
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50) NOT NULL DEFAULT 'PENDING_ENROLLMENT'::character varying
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid
- `reward_currency_id` bigint

**Constraints**
- PK PRIMARY KEY (referrals_id)
- FK `referrals_referred_member_id_fkey` FOREIGN KEY (referred_member_id) REFERENCES members(members_id)
- FK `referrals_referrer_member_id_fkey` FOREIGN KEY (referrer_member_id) REFERENCES members(members_id)
- FK `referrals_reward_currency_id_fkey` FOREIGN KEY (reward_currency_id) REFERENCES currencies(currencies_id)
- FK `referrals_reward_payment_id_fkey` FOREIGN KEY (reward_payment_id) REFERENCES payments(payments_id)
- UNIQUE `referrals_uuid_key` UNIQUE (uuid)
- CHECK `chk_referrals_expired_unreferred` CHECK ((((status)::text <> 'EXPIRED'::text) OR (referred_member_id IS NULL)))
- CHECK `chk_referrals_not_self` CHECK (((referred_member_id IS NULL) OR (referred_member_id <> referrer_member_id)))
- CHECK `chk_referrals_pending_no_referred` CHECK ((((status)::text <> 'PENDING_ENROLLMENT'::text) OR ((referred_member_id IS NULL) AND (enrolled_at IS NULL))))
- CHECK `chk_referrals_registered_has_referred` CHECK ((((status)::text <> ALL ((ARRAY['REGISTERED'::character varying, 'REWARD_GRANTED'::character varying, 'VOIDED'::character varying])::text[])) OR ((referred_member_id IS NOT NULL) AND (enrolled_at IS NOT NULL))))
- CHECK `chk_referrals_reward_consistency` CHECK ((((status)::text <> 'REWARD_GRANTED'::text) OR ((reward_payment_id IS NOT NULL) AND (reward_granted_at IS NOT NULL))))
- CHECK `chk_referrals_reward_currency_paired` CHECK (((reward_flat_amount IS NULL) OR (reward_currency_id IS NOT NULL)))
- CHECK `chk_referrals_reward_pct_xor_flat` CHECK ((((reward_pct IS NULL) AND (reward_flat_amount IS NULL)) OR ((reward_pct IS NOT NULL) AND (reward_flat_amount IS NULL)) OR ((reward_pct IS NULL) AND (reward_flat_amount IS NOT NULL))))
- CHECK `chk_referrals_void_has_reason` CHECK ((((status)::text <> 'VOIDED'::text) OR (void_reason IS NOT NULL)))
- CHECK `referrals_reward_flat_amount_check` CHECK (((reward_flat_amount IS NULL) OR (reward_flat_amount >= (0)::numeric)))
- CHECK `referrals_reward_pct_check` CHECK (((reward_pct IS NULL) OR ((reward_pct >= (0)::numeric) AND (reward_pct <= (100)::numeric))))
- CHECK `referrals_status_check` CHECK (((status)::text = ANY ((ARRAY['PENDING_ENROLLMENT'::character varying, 'REGISTERED'::character varying, 'EXPIRED'::character varying, 'REWARD_GRANTED'::character varying, 'VOIDED'::character varying])::text[])))

**Indexes**
- `idx_referrals_code` btree (referral_code)
- `idx_referrals_pending_expiration` btree (expires_at) WHERE (((status)::text = 'PENDING_ENROLLMENT'::text) AND (expires_at IS NOT NULL))
- `idx_referrals_referred` btree (referred_member_id) WHERE (referred_member_id IS NOT NULL)
- `idx_referrals_referrer_created` btree (referrer_member_id, created_at DESC) WHERE is_active
- `uniq_referrals_referrer_referred_active` UNIQUE btree (referrer_member_id, referred_member_id) WHERE ((referred_member_id IS NOT NULL) AND ((status)::text <> ALL ((ARRAY['EXPIRED'::character varying, 'VOIDED'::character varying])::text[])))

**Triggers**
- `trg_referrals_updated_at` BEFORE UPDATE ON app.referrals FOR EACH ROW EXECUTE FUNCTION set_updated_at()

## report_audit_log

**Columns**
- `report_audit_log_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `report_type` character varying(80) NOT NULL
- `entity_key` character varying(80)
- `entity_id` bigint
- `entity_uuid` uuid
- `entity_identifier` character varying(120)
- `format` character varying(10) NOT NULL
- `parameters_json` jsonb
- `actor_id` bigint
- `login_audit_log_id` bigint
- `attached_file_id` bigint
- `file_name` character varying(255)
- `size_bytes` bigint
- `generated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_at` timestamp with time zone NOT NULL DEFAULT now()

**Constraints**
- PK PRIMARY KEY (report_audit_log_id)
- FK `report_audit_log_actor_id_fkey` FOREIGN KEY (actor_id) REFERENCES users(users_id)
- FK `report_audit_log_attached_file_id_fkey` FOREIGN KEY (attached_file_id) REFERENCES attached_files(attached_files_id)
- FK `report_audit_log_login_audit_log_id_fkey` FOREIGN KEY (login_audit_log_id) REFERENCES login_audit_log(login_audit_log_id)
- UNIQUE `report_audit_log_uuid_key` UNIQUE (uuid)
- CHECK `chk_report_audit_format` CHECK (((format)::text = ANY ((ARRAY['PDF'::character varying, 'XLSX'::character varying])::text[])))

**Indexes**
- `idx_report_audit_actor_generated` btree (actor_id, generated_at DESC)
- `idx_report_audit_entity_generated` btree (entity_key, entity_uuid, generated_at DESC)
- `idx_report_audit_entity_id` btree (entity_key, entity_id) WHERE (entity_id IS NOT NULL)
- `idx_report_audit_file` btree (attached_file_id)
- `idx_report_audit_generated_at` btree (generated_at DESC)
- `idx_report_audit_login_audit` btree (login_audit_log_id)

**Referenced by** `report_share`.(report_audit_log_id)

## report_share

**Columns**
- `report_share_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `report_audit_log_id` bigint NOT NULL
- `share_token` character varying(120) NOT NULL
- `share_link` character varying(500)
- `recipients` jsonb
- `expires_at` timestamp with time zone NOT NULL
- `revoked_at` timestamp with time zone
- `created_by` bigint
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `status` character varying(20) NOT NULL DEFAULT 'ACTIVE'::character varying

**Constraints**
- PK PRIMARY KEY (report_share_id)
- FK `report_share_created_by_fkey` FOREIGN KEY (created_by) REFERENCES users(users_id)
- FK `report_share_report_audit_log_id_fkey` FOREIGN KEY (report_audit_log_id) REFERENCES report_audit_log(report_audit_log_id)
- UNIQUE `report_share_share_token_key` UNIQUE (share_token)
- UNIQUE `report_share_uuid_key` UNIQUE (uuid)
- CHECK `chk_report_share_revoked` CHECK ((((status)::text = 'REVOKED'::text) = (revoked_at IS NOT NULL)))
- CHECK `chk_report_share_status` CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'EXPIRED'::character varying, 'REVOKED'::character varying])::text[])))

**Indexes**
- `idx_report_share_report` btree (report_audit_log_id)
- `idx_report_share_status_expiry` btree (status, expires_at)

## role_permissions

**Columns**
- `role_permissions_id` bigint NOT NULL IDENTITY
- `role_id` bigint NOT NULL
- `permission_id` bigint NOT NULL
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid

**Constraints**
- PK PRIMARY KEY (role_permissions_id)
- FK `role_permissions_permission_id_fkey` FOREIGN KEY (permission_id) REFERENCES permissions(permissions_id) ON DELETE CASCADE
- FK `role_permissions_role_id_fkey` FOREIGN KEY (role_id) REFERENCES roles(roles_id)
- UNIQUE `role_permissions_role_id_permission_id_key` UNIQUE (role_id, permission_id)

**Indexes**
- `idx_role_permissions_permission_id` btree (permission_id)

**Triggers**
- `trg_role_permissions_updated_at` BEFORE UPDATE ON app.role_permissions FOR EACH ROW EXECUTE FUNCTION set_updated_at()

## roles

**Columns**
- `roles_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `name` character varying(50) NOT NULL
- `description` character varying(200)
- `is_active` boolean NOT NULL DEFAULT true
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid

**Constraints**
- PK PRIMARY KEY (roles_id)
- UNIQUE `roles_name_key` UNIQUE (name)
- UNIQUE `roles_uuid_key` UNIQUE (uuid)

**Indexes**
- `idx_roles_is_active` btree (is_active) WHERE is_active

**Triggers**
- `trg_roles_updated_at` BEFORE UPDATE ON app.roles FOR EACH ROW EXECUTE FUNCTION set_updated_at()

**Referenced by** `login_audit_log`.(active_role_id), `role_permissions`.(role_id), `user_roles`.(role_id), `users`.(default_role_id)

## scheduled_job_runs

**Columns**
- `scheduled_job_runs_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `scheduled_job_id` bigint NOT NULL
- `started_at` timestamp with time zone NOT NULL DEFAULT now()
- `finished_at` timestamp with time zone
- `duration_ms` bigint
- `outcome` character varying(20) NOT NULL DEFAULT 'RUNNING'::character varying
- `triggered_by` character varying(20) NOT NULL
- `triggered_by_user_uuid` uuid
- `summary` jsonb
- `error_message` text
- `is_active` boolean NOT NULL DEFAULT true
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid
- `attempt_count` integer NOT NULL DEFAULT 1

**Constraints**
- PK PRIMARY KEY (scheduled_job_runs_id)
- FK `scheduled_job_runs_scheduled_job_id_fkey` FOREIGN KEY (scheduled_job_id) REFERENCES scheduled_jobs(scheduled_jobs_id)
- UNIQUE `scheduled_job_runs_uuid_key` UNIQUE (uuid)
- CHECK `chk_runs_finished_after_started` CHECK (((finished_at IS NULL) OR (finished_at >= started_at)))
- CHECK `chk_runs_manual_has_user` CHECK ((((triggered_by)::text <> 'MANUAL'::text) OR (triggered_by_user_uuid IS NOT NULL)))
- CHECK `chk_runs_terminal_has_finished_at` CHECK ((((outcome)::text = 'RUNNING'::text) OR (finished_at IS NOT NULL)))
- CHECK `scheduled_job_runs_outcome_check` CHECK (((outcome)::text = ANY ((ARRAY['RUNNING'::character varying, 'SUCCESS'::character varying, 'FAILED'::character varying, 'TIMEOUT'::character varying, 'SKIPPED_CONCURRENT'::character varying, 'CANCELED'::character varying])::text[])))
- CHECK `scheduled_job_runs_triggered_by_check` CHECK (((triggered_by)::text = ANY ((ARRAY['SCHEDULED'::character varying, 'MANUAL'::character varying, 'STARTUP'::character varying])::text[])))

**Indexes**
- `idx_scheduled_job_runs_created` btree (created_at DESC)
- `idx_scheduled_job_runs_job_started` btree (scheduled_job_id, started_at DESC)
- `idx_scheduled_job_runs_running` btree (scheduled_job_id) WHERE ((outcome)::text = 'RUNNING'::text)

**Triggers**
- `trg_scheduled_job_runs_updated_at` BEFORE UPDATE ON app.scheduled_job_runs FOR EACH ROW EXECUTE FUNCTION set_updated_at()

## scheduled_jobs

**Columns**
- `scheduled_jobs_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `code` character varying(80) NOT NULL
- `display_name` character varying(120) NOT NULL
- `description` text
- `cron_expression` character varying(120) NOT NULL
- `timezone` character varying(60) NOT NULL DEFAULT 'America/Caracas'::character varying
- `enabled` boolean NOT NULL DEFAULT true
- `allow_concurrent` boolean NOT NULL DEFAULT false
- `max_sync_seconds` integer NOT NULL DEFAULT 30
- `lock_held` boolean NOT NULL DEFAULT false
- `last_run_at` timestamp with time zone
- `last_run_status` character varying(20)
- `next_run_at` timestamp with time zone
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50) NOT NULL DEFAULT 'ENABLED'::character varying
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid
- `parameters` jsonb NOT NULL DEFAULT '{}'::jsonb
- `max_retry_attempts` integer NOT NULL DEFAULT 5
- `retry_delay_seconds` integer NOT NULL DEFAULT 10

**Constraints**
- PK PRIMARY KEY (scheduled_jobs_id)
- UNIQUE `scheduled_jobs_code_key` UNIQUE (code)
- UNIQUE `scheduled_jobs_uuid_key` UNIQUE (uuid)
- CHECK `scheduled_jobs_max_retry_attempts_check` CHECK (((max_retry_attempts >= 0) AND (max_retry_attempts <= 10)))
- CHECK `scheduled_jobs_max_sync_seconds_check` CHECK ((max_sync_seconds >= 0))
- CHECK `scheduled_jobs_retry_delay_seconds_check` CHECK (((retry_delay_seconds >= 0) AND (retry_delay_seconds <= 3600)))
- CHECK `scheduled_jobs_status_check` CHECK (((status)::text = ANY ((ARRAY['ENABLED'::character varying, 'DISABLED'::character varying])::text[])))

**Indexes**
- `idx_scheduled_jobs_enabled` btree (enabled) WHERE (is_active AND enabled)

**Triggers**
- `trg_scheduled_jobs_updated_at` BEFORE UPDATE ON app.scheduled_jobs FOR EACH ROW EXECUTE FUNCTION set_updated_at()

**Referenced by** `scheduled_job_runs`.(scheduled_job_id)

## security_policies

**Columns**
- `security_policies_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `max_login_attempts` integer NOT NULL DEFAULT 5
- `lockout_duration_minutes` integer NOT NULL DEFAULT 30
- `days_password_expires` integer NOT NULL DEFAULT 90
- `password_history_count` integer NOT NULL DEFAULT 5
- `max_concurrent_sessions` integer NOT NULL DEFAULT 3
- `is_active` boolean NOT NULL DEFAULT true
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid

**Constraints**
- PK PRIMARY KEY (security_policies_id)
- UNIQUE `security_policies_uuid_key` UNIQUE (uuid)
- CHECK `chk_security_policies_days_password_expires` CHECK ((days_password_expires >= 0))
- CHECK `chk_security_policies_lockout_duration_minutes` CHECK ((lockout_duration_minutes >= 1))
- CHECK `chk_security_policies_max_concurrent_sessions` CHECK ((max_concurrent_sessions >= 1))
- CHECK `chk_security_policies_max_login_attempts` CHECK ((max_login_attempts >= 1))
- CHECK `chk_security_policies_password_history_count` CHECK ((password_history_count >= 0))

**Triggers**
- `trg_security_policies_updated_at` BEFORE UPDATE ON app.security_policies FOR EACH ROW EXECUTE FUNCTION set_updated_at()

## states

**Columns**
- `states_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `country_id` bigint NOT NULL
- `code` character varying(10) NOT NULL
- `name` character varying(100) NOT NULL
- `is_active` boolean NOT NULL DEFAULT true
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid

**Constraints**
- PK PRIMARY KEY (states_id)
- FK `states_country_id_fkey` FOREIGN KEY (country_id) REFERENCES countries(countries_id)
- UNIQUE `states_country_id_code_key` UNIQUE (country_id, code)
- UNIQUE `states_country_id_name_key` UNIQUE (country_id, name)
- UNIQUE `states_uuid_key` UNIQUE (uuid)

**Indexes**
- `idx_states_is_active` btree (is_active) WHERE is_active

**Triggers**
- `trg_states_updated_at` BEFORE UPDATE ON app.states FOR EACH ROW EXECUTE FUNCTION set_updated_at()

**Referenced by** `cities`.(state_id), `holidays`.(state_id)

## subsidies

**Columns**
- `subsidies_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL
- `member_id` bigint NOT NULL
- `monthly_percentage` numeric(5,2)
- `inscription_percentage` numeric(5,2)
- `max_exonerated_beneficiaries` integer
- `reason` text NOT NULL
- `authorized_by` bigint
- `valid_from` date NOT NULL
- `valid_until` date
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50)
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid

**Constraints**
- PK PRIMARY KEY (subsidies_id)
- FK `subsidies_authorized_by_fkey` FOREIGN KEY (authorized_by) REFERENCES users(users_id)
- FK `subsidies_member_id_fkey` FOREIGN KEY (member_id) REFERENCES members(members_id)
- UNIQUE `subsidies_uuid_key` UNIQUE (uuid)
- CHECK `chk_subsidies_covers_something` CHECK (((monthly_percentage IS NOT NULL) OR (inscription_percentage IS NOT NULL)))
- CHECK `chk_subsidies_inscription_pct` CHECK (((inscription_percentage IS NULL) OR ((inscription_percentage >= (0)::numeric) AND (inscription_percentage <= (100)::numeric))))
- CHECK `chk_subsidies_max_exon` CHECK (((max_exonerated_beneficiaries IS NULL) OR (max_exonerated_beneficiaries >= 0)))
- CHECK `chk_subsidies_monthly_pct` CHECK (((monthly_percentage IS NULL) OR ((monthly_percentage >= (0)::numeric) AND (monthly_percentage <= (100)::numeric))))
- CHECK `chk_subsidies_valid_window` CHECK (((valid_until IS NULL) OR (valid_until >= valid_from)))

**Indexes**
- `idx_subsidies_member_window` btree (member_id, valid_from, valid_until) WHERE is_active

**Triggers**
- `trg_subsidies_updated_at` BEFORE UPDATE ON app.subsidies FOR EACH ROW EXECUTE FUNCTION set_updated_at()

**Referenced by** `subsidy_audit_log`.(subsidy_id), `subsidy_beneficiaries`.(subsidy_id)

## subsidy_audit_log

**Columns**
- `subsidy_audit_log_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL
- `subsidy_id` bigint NOT NULL
- `action` character varying(20) NOT NULL
- `actor_id` bigint
- `before_json` jsonb
- `after_json` jsonb
- `reason` text
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50)
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid

**Constraints**
- PK PRIMARY KEY (subsidy_audit_log_id)
- FK `subsidy_audit_log_actor_id_fkey` FOREIGN KEY (actor_id) REFERENCES users(users_id)
- FK `subsidy_audit_log_subsidy_id_fkey` FOREIGN KEY (subsidy_id) REFERENCES subsidies(subsidies_id)
- UNIQUE `subsidy_audit_log_uuid_key` UNIQUE (uuid)
- CHECK `chk_subsidy_audit_action` CHECK (((action)::text = ANY ((ARRAY['CREATED'::character varying, 'MODIFIED'::character varying, 'REVOKED'::character varying])::text[])))

**Indexes**
- `idx_subsidy_audit_log_subsidy` btree (subsidy_id, created_at DESC)

**Triggers**
- `trg_subsidy_audit_log_updated_at` BEFORE UPDATE ON app.subsidy_audit_log FOR EACH ROW EXECUTE FUNCTION set_updated_at()

## subsidy_beneficiaries

**Columns**
- `subsidy_beneficiaries_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL
- `subsidy_id` bigint NOT NULL
- `beneficiary_id` bigint NOT NULL
- `monthly_percentage` numeric(5,2)
- `inscription_percentage` numeric(5,2)
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50)
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid

**Constraints**
- PK PRIMARY KEY (subsidy_beneficiaries_id)
- FK `subsidy_beneficiaries_beneficiary_id_fkey` FOREIGN KEY (beneficiary_id) REFERENCES beneficiaries(beneficiaries_id)
- FK `subsidy_beneficiaries_subsidy_id_fkey` FOREIGN KEY (subsidy_id) REFERENCES subsidies(subsidies_id) ON DELETE CASCADE
- UNIQUE `subsidy_beneficiaries_uuid_key` UNIQUE (uuid)
- UNIQUE `uq_subsidy_beneficiaries` UNIQUE (subsidy_id, beneficiary_id)
- CHECK `chk_subsidy_ben_covers_something` CHECK (((monthly_percentage IS NOT NULL) OR (inscription_percentage IS NOT NULL)))
- CHECK `chk_subsidy_ben_inscription_pct` CHECK (((inscription_percentage IS NULL) OR ((inscription_percentage >= (0)::numeric) AND (inscription_percentage <= (100)::numeric))))
- CHECK `chk_subsidy_ben_monthly_pct` CHECK (((monthly_percentage IS NULL) OR ((monthly_percentage >= (0)::numeric) AND (monthly_percentage <= (100)::numeric))))

**Indexes**
- `idx_subsidy_beneficiaries_beneficiary` btree (beneficiary_id) WHERE is_active

**Triggers**
- `trg_subsidy_beneficiaries_updated_at` BEFORE UPDATE ON app.subsidy_beneficiaries FOR EACH ROW EXECUTE FUNCTION set_updated_at()

## system_configs

**Columns**
- `system_configs_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `report_footer` text
- `is_active` boolean NOT NULL DEFAULT true
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid
- `report_audit_mode` character varying(20) NOT NULL DEFAULT 'PER_ENTITY'::character varying
- `login_audit_enabled` boolean NOT NULL DEFAULT true
- `login_session_expiration_days` integer NOT NULL DEFAULT 30
- `default_sort` jsonb — Global fallback default sort, same shape as entity_config.default_sort: [{"field":"createdAt","direction":"DESC"}]. Restricted to common columns (id, uuid, createdAt, updatedAt, createdBy, updatedBy) since it is not validated against any single entity's field set. NULL means "no global default configured" — callers fall back to createdAt DESC in code.
- `audit_create_mode` character varying(20) NOT NULL DEFAULT 'PER_ENTITY'::character varying
- `audit_update_mode` character varying(20) NOT NULL DEFAULT 'PER_ENTITY'::character varying
- `audit_delete_mode` character varying(20) NOT NULL DEFAULT 'PER_ENTITY'::character varying
- `capture_before_after_mode` character varying(20) NOT NULL DEFAULT 'PER_ENTITY'::character varying
- `patient_medical_access_enabled` boolean NOT NULL DEFAULT false — Activa el acceso de los aliados médicos a la historia de sus pacientes (ADR 0032). Apagado hasta que legal confirme la cláusula del Acuerdo B2C.
- `pdf_max_concurrent_renders` integer NOT NULL DEFAULT 5 — Max generic PDF documents rendered at the same time; 0 or -1 = no cap
- `pdf_render_wait_seconds` integer NOT NULL DEFAULT 10 — Seconds a PDF request waits for a free render slot before 503; 0 or -1 = no time limit
- `generated_report_retention_days` integer NOT NULL DEFAULT 30 — Days a generated report stays stored in R2 before GENERATED_REPORT_PURGE deletes it (the audit row stays)

**Constraints**
- PK PRIMARY KEY (system_configs_id)
- UNIQUE `system_configs_uuid_key` UNIQUE (uuid)
- CHECK `chk_system_configs_audit_create_mode` CHECK (((audit_create_mode)::text = ANY ((ARRAY['PER_ENTITY'::character varying, 'FORCE_ENABLED'::character varying, 'FORCE_DISABLED'::character varying])::text[])))
- CHECK `chk_system_configs_audit_delete_mode` CHECK (((audit_delete_mode)::text = ANY ((ARRAY['PER_ENTITY'::character varying, 'FORCE_ENABLED'::character varying, 'FORCE_DISABLED'::character varying])::text[])))
- CHECK `chk_system_configs_audit_update_mode` CHECK (((audit_update_mode)::text = ANY ((ARRAY['PER_ENTITY'::character varying, 'FORCE_ENABLED'::character varying, 'FORCE_DISABLED'::character varying])::text[])))
- CHECK `chk_system_configs_capture_before_after_mode` CHECK (((capture_before_after_mode)::text = ANY ((ARRAY['PER_ENTITY'::character varying, 'FORCE_ENABLED'::character varying, 'FORCE_DISABLED'::character varying])::text[])))
- CHECK `chk_system_configs_generated_report_retention_days` CHECK ((generated_report_retention_days > 0))
- CHECK `chk_system_configs_login_session_expiration_days` CHECK ((login_session_expiration_days > 0))
- CHECK `chk_system_configs_pdf_max_concurrent_renders` CHECK ((pdf_max_concurrent_renders >= '-1'::integer))
- CHECK `chk_system_configs_pdf_render_wait_seconds` CHECK ((pdf_render_wait_seconds >= '-1'::integer))
- CHECK `chk_system_configs_report_audit_mode` CHECK (((report_audit_mode)::text = ANY ((ARRAY['PER_ENTITY'::character varying, 'FORCE_ENABLED'::character varying, 'FORCE_DISABLED'::character varying])::text[])))

**Triggers**
- `trg_system_configs_updated_at` BEFORE UPDATE ON app.system_configs FOR EACH ROW EXECUTE FUNCTION set_updated_at()

## terms_acceptances

**Columns**
- `terms_acceptances_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `user_id` bigint NOT NULL
- `terms_versions_id` bigint NOT NULL
- `accepted_at` timestamp with time zone NOT NULL DEFAULT now()
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50)
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid
- `ip_address` character varying(45) — IP del cliente al aceptar (IPv4 o IPv6)
- `user_agent` character varying(400) — User-Agent del navegador al aceptar (truncado a 400)
- `channel` character varying(20) NOT NULL DEFAULT 'PORTAL'::character varying — Canal de aceptación: PORTAL (modal en la app), SELF_SERVICE (autoafiliación pública), ASSISTED (cargado por un asesor), ADMIN
- `membership_id` bigint — Membresía para la que se aceptó (autoafiliación); NULL en aceptaciones desde el portal
- `ally_agreement_id` bigint — Convenio de aliado para el que se aceptó el contrato ALIADO; NULL para AFILIADO/ASESOR y para aceptaciones ALIADO anteriores a V189 que no se pudieron vincular a un único convenio
- `rendered_content` text — Texto del contrato tal como se mostró y aceptó (variables ya resueltas); NULL en aceptaciones anteriores a V189
- `content_sha256` character varying(64) — SHA-256 en hexadecimal (UTF-8) de rendered_content, para probar que el texto aceptado no se alteró

**Constraints**
- PK PRIMARY KEY (terms_acceptances_id)
- FK `terms_acceptances_ally_agreement_id_fkey` FOREIGN KEY (ally_agreement_id) REFERENCES ally_agreements(ally_agreements_id)
- FK `terms_acceptances_membership_id_fkey` FOREIGN KEY (membership_id) REFERENCES memberships(memberships_id)
- FK `terms_acceptances_terms_versions_id_fkey` FOREIGN KEY (terms_versions_id) REFERENCES terms_versions(terms_versions_id)
- FK `terms_acceptances_user_id_fkey` FOREIGN KEY (user_id) REFERENCES users(users_id)
- UNIQUE `terms_acceptances_uuid_key` UNIQUE (uuid)
- CHECK `chk_terms_acceptances_channel` CHECK (((channel)::text = ANY ((ARRAY['PORTAL'::character varying, 'SELF_SERVICE'::character varying, 'ASSISTED'::character varying, 'ADMIN'::character varying])::text[])))
- CHECK `chk_terms_acceptances_content_sha256` CHECK (((content_sha256 IS NULL) OR ((content_sha256)::text ~ '^[0-9a-f]{64}$'::text)))

**Indexes**
- `idx_terms_acceptances_ally_agreement` btree (ally_agreement_id) WHERE (ally_agreement_id IS NOT NULL)
- `idx_terms_acceptances_membership` btree (membership_id) WHERE (membership_id IS NOT NULL)
- `idx_terms_acceptances_user` btree (user_id)
- `uq_terms_acceptances_user_version` UNIQUE btree (user_id, terms_versions_id) WHERE (ally_agreement_id IS NULL)
- `uq_terms_acceptances_user_version_agreement` UNIQUE btree (user_id, terms_versions_id, ally_agreement_id) WHERE (ally_agreement_id IS NOT NULL)

**Triggers**
- `trg_terms_acceptances_updated_at` BEFORE UPDATE ON app.terms_acceptances FOR EACH ROW EXECUTE FUNCTION set_updated_at()

## terms_versions

**Columns**
- `terms_versions_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `term_type` character varying(20) NOT NULL
- `title` character varying(200) NOT NULL
- `content_markdown` text NOT NULL
- `is_public` boolean NOT NULL DEFAULT false — Visible en el endpoint público de la web (GET /v1/public/terms/{type}); NO decide si se pide aceptación
- `valid_from` timestamp with time zone NOT NULL
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50)
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid
- `ally_type_id` bigint — Solo ALIADO: plantilla restringida a aliados de este tipo; NULL = cualquier tipo
- `segment` character varying(3) — Solo ALIADO: plantilla restringida al segmento B2B (Comercial) o B2P (Profesional); NULL = cualquier segmento
- `ally_id` bigint — Solo ALIADO: plantilla hecha para un aliado específico; NULL = cualquier aliado
- `is_mandatory` boolean NOT NULL DEFAULT false — TRUE = debe aceptarse para usar la plataforma; FALSE = se puede posponer (bloquea solo el módulo del contrato)

**Constraints**
- PK PRIMARY KEY (terms_versions_id)
- FK `terms_versions_ally_id_fkey` FOREIGN KEY (ally_id) REFERENCES allies(allies_id)
- FK `terms_versions_ally_type_id_fkey` FOREIGN KEY (ally_type_id) REFERENCES ally_types(ally_types_id)
- UNIQUE `terms_versions_uuid_key` UNIQUE (uuid)
- CHECK `chk_terms_versions_scope_only_aliado` CHECK ((((term_type)::text = 'ALIADO'::text) OR ((ally_type_id IS NULL) AND (segment IS NULL) AND (ally_id IS NULL))))
- CHECK `chk_terms_versions_segment` CHECK (((segment IS NULL) OR ((segment)::text = ANY ((ARRAY['B2B'::character varying, 'B2P'::character varying])::text[]))))
- CHECK `chk_terms_versions_term_type` CHECK (((term_type)::text = ANY ((ARRAY['AFILIADO'::character varying, 'ASESOR'::character varying, 'ALIADO'::character varying])::text[])))

**Indexes**
- `idx_terms_versions_scope_valid_from` btree (term_type, ally_type_id, segment, ally_id, valid_from DESC)
- `idx_terms_versions_type_valid_from` btree (term_type, valid_from DESC)

**Triggers**
- `trg_terms_versions_updated_at` BEFORE UPDATE ON app.terms_versions FOR EACH ROW EXECUTE FUNCTION set_updated_at()

**Referenced by** `affiliation_requests`.(terms_versions_id), `ally_agreements`.(terms_version_id), `terms_acceptances`.(terms_versions_id)

## user_password_history

**Columns**
- `user_password_history_id` bigint NOT NULL IDENTITY
- `user_id` bigint NOT NULL
- `password_hash` character varying(255) NOT NULL
- `expires_at` timestamp with time zone
- `created_at` timestamp with time zone NOT NULL DEFAULT now()

**Constraints**
- PK PRIMARY KEY (user_password_history_id)
- FK `user_password_history_user_id_fkey` FOREIGN KEY (user_id) REFERENCES users(users_id)

**Indexes**
- `idx_user_password_history_user_id` btree (user_id, created_at DESC)

## user_roles

**Columns**
- `user_roles_id` bigint NOT NULL IDENTITY
- `user_id` bigint NOT NULL
- `role_id` bigint NOT NULL
- `assigned_by` uuid
- `expires_at` timestamp with time zone
- `is_active` boolean NOT NULL DEFAULT true
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid

**Constraints**
- PK PRIMARY KEY (user_roles_id)
- FK `user_roles_role_id_fkey` FOREIGN KEY (role_id) REFERENCES roles(roles_id)
- FK `user_roles_user_id_fkey` FOREIGN KEY (user_id) REFERENCES users(users_id)
- UNIQUE `user_roles_user_id_role_id_key` UNIQUE (user_id, role_id)

**Indexes**
- `idx_user_roles_role_id` btree (role_id)

**Triggers**
- `trg_user_roles_updated_at` BEFORE UPDATE ON app.user_roles FOR EACH ROW EXECUTE FUNCTION set_updated_at()

## users

**Columns**
- `users_id` bigint NOT NULL IDENTITY
- `uuid` uuid NOT NULL DEFAULT gen_random_uuid()
- `email` citext NOT NULL
- `password_hash` character varying(255) NOT NULL
- `default_role_id` bigint
- `failed_login_attempts` integer NOT NULL DEFAULT 0
- `locked_until` timestamp with time zone
- `password_reset_token` character varying(255)
- `password_reset_expires_at` timestamp with time zone
- `last_login_at` timestamp with time zone
- `password_never_expires` boolean NOT NULL DEFAULT false
- `is_active` boolean NOT NULL DEFAULT true
- `status` character varying(50) NOT NULL DEFAULT 'ACTIVE'::character varying
- `created_at` timestamp with time zone NOT NULL DEFAULT now()
- `updated_at` timestamp with time zone NOT NULL DEFAULT now()
- `created_by` uuid
- `updated_by` uuid
- `person_id` bigint NOT NULL
- `login_token_hash` character varying(255) — SHA-256 of the single-use access-link token (15 min TTL); NULL when none is pending.
- `login_token_expires_at` timestamp with time zone — Expiry of login_token_hash; the link is also invalidated by its first use.

**Constraints**
- PK PRIMARY KEY (users_id)
- FK `users_default_role_id_fkey` FOREIGN KEY (default_role_id) REFERENCES roles(roles_id)
- FK `users_person_id_fkey` FOREIGN KEY (person_id) REFERENCES persons(persons_id)
- UNIQUE `uniq_users_person` UNIQUE (person_id)
- UNIQUE `users_email_key` UNIQUE (email)
- UNIQUE `users_uuid_key` UNIQUE (uuid)
- CHECK `users_status_check` CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'SUSPENDED'::character varying, 'LOCKED'::character varying])::text[])))

**Indexes**
- `idx_users_default_role` btree (default_role_id)
- `idx_users_locked_until` btree (locked_until) WHERE (locked_until IS NOT NULL)
- `idx_users_login_token_hash` btree (login_token_hash) WHERE (login_token_hash IS NOT NULL)
- `idx_users_password_reset_token` btree (password_reset_token) WHERE (password_reset_token IS NOT NULL)

**Triggers**
- `trg_users_updated_at` BEFORE UPDATE ON app.users FOR EACH ROW EXECUTE FUNCTION set_updated_at()

**Referenced by** `affiliation_request_logs`.(actor_user_id), `affiliation_requests`.(reviewed_by), `affiliation_requests`.(user_id), `ally_patient_links`.(created_by_user_id), `ally_product_review_log`.(actor_user_id), `ally_products`.(reviewed_by), `ally_users`.(user_id), `attached_files`.(published_by), `attached_files`.(uploaded_by), `beneficiaries`.(user_id), `commissions`.(approved_by_user_id), `corporate_contracts`.(contact_user_id), `data_change_audit_log`.(actor_id), `file_download_links`.(created_by), `login_audit_log`.(user_id), `medical_record_access_log`.(accessed_by_user_id), `member_documents`.(uploaded_by), `member_promoter_assignments`.(actor_user_id), `membership_promotions`.(assigned_by_user_id), `notifications`.(recipient_user_id), `payment_credit_claims`.(opened_by), `payment_credit_claims`.(resolved_by), `payment_lines`.(reviewed_by), `payments`.(discounted_by), `payments`.(payer_user_id), `payments`.(reviewed_by), `promoter_supervisor_assignments`.(actor_user_id), `promoters`.(user_id), `report_audit_log`.(actor_id), `report_share`.(created_by), `subsidies`.(authorized_by), `subsidy_audit_log`.(actor_id), `terms_acceptances`.(user_id), `user_password_history`.(user_id), `user_roles`.(user_id)

# Views

### commission_period_summary

```sql
 SELECT c.promoter_id,
    c.period_strategy,
    c.period_start,
    c.period_end,
    count(*) AS commission_count,
    sum(c.amount) AS total_amount,
    min(c.currency_id) AS currency_id
   FROM commissions c
  WHERE c.is_active = true AND c.status::text <> 'VOIDED'::text
  GROUP BY c.promoter_id, c.period_strategy, c.period_start, c.period_end;
```

### digital_cards_view

```sql
 SELECT m.uuid AS member_uuid,
    u.uuid AS user_uuid,
    p.full_name,
    p.document_type,
    p.document_number,
    pl.name AS plan_name,
    ms.status AS membership_status,
    ms.next_due_date,
    m.enrolled_at AS member_since,
    ms.expires_at,
    ms.inscription_due_since
   FROM members m
     JOIN persons p ON p.persons_id = m.person_id
     LEFT JOIN users u ON u.person_id = p.persons_id
     LEFT JOIN memberships ms ON ms.member_id = m.members_id AND ms.is_active = true
     LEFT JOIN plans pl ON pl.plans_id = ms.plan_id
  WHERE m.is_active = true;
```

### v_report_commissions

```sql
 SELECT c.commissions_id,
    c.uuid AS commission_uuid,
    c.status AS commission_status,
    c.applies_to,
    c.period_strategy,
    c.period_start,
    c.period_end,
    c.earned_at,
    c.amount AS commission_amount,
    cur.code AS currency,
    cur.symbol AS currency_symbol,
    c.calculation_basis,
    c.commission_pct,
    c.flat_amount,
    COALESCE(c.tier_name_snapshot, 'Sin Nivel'::character varying) AS tier_name,
    c.payout_payment_id,
    payout_p.uuid AS payout_payment_uuid,
    payout_p.payment_date AS payout_date,
    payout_p.status AS payout_payment_status,
    payout_line.payout_method_code,
    payout_line.payout_method_name,
    payout_line.payout_bank_name,
    COALESCE(payout_line.reference_number, c.payout_reference::text) AS payout_reference,
    c.paid_at,
    c.exchange_rate_at_earned,
    c.earned_rate_date,
    c.exchange_rate_at_paid,
    c.paid_rate_date,
    c.voided_at,
    c.void_reason,
    c.admin_notes,
    c.created_at AS commission_created_at,
    pr.promoters_id,
    pr.uuid AS promoter_uuid,
    pr.display_name AS promoter_name,
    pr.referral_code AS promoter_referral_code,
    COALESCE(pr_p.full_name, pr.display_name) AS promoter_person_name,
    COALESCE((pr_p.document_type::text || '-'::text) || pr_p.document_number::text, 'N/A'::text) AS promoter_document,
    COALESCE(pr.phone, pr_p.phone, 'N/A'::character varying) AS promoter_phone,
    COALESCE(pr.email::text, pr_p.email::text, 'N/A'::text) AS promoter_email,
    COALESCE(pt.name, 'Estándar'::character varying) AS promoter_type_name,
    m.members_id,
    m.uuid AS member_uuid,
    m_p.full_name AS member_name,
    (m_p.document_type::text || '-'::text) || m_p.document_number::text AS member_document,
    COALESCE(m_p.phone, 'N/A'::character varying) AS member_phone,
    COALESCE(m_p.email::text, 'N/A'::text) AS member_email,
    p.payments_id,
    p.uuid AS payment_uuid,
    p.payment_date,
    p.received_at AS payment_received_at,
    origin_line.payment_method,
    origin_line.payment_method_name,
    origin_line.payment_bank_name,
    origin_line.payment_reference,
    p.amount AS payment_amount,
    p.status AS payment_status,
    ms.memberships_id,
    ms.uuid AS membership_uuid,
    pl.plans_id,
    pl.code AS plan_code,
    pl.name AS plan_name,
    pl.type AS plan_type
   FROM commissions c
     JOIN currencies cur ON c.currency_id = cur.currencies_id
     JOIN promoters pr ON c.promoter_id = pr.promoters_id
     LEFT JOIN persons pr_p ON pr.person_id = pr_p.persons_id
     LEFT JOIN promoter_types pt ON pr.promoter_type_id = pt.promoter_types_id
     JOIN members m ON c.member_id = m.members_id
     JOIN persons m_p ON m.person_id = m_p.persons_id
     JOIN payments p ON c.payment_id = p.payments_id
     LEFT JOIN LATERAL ( SELECT string_agg(DISTINCT origin_pm.code::text, ', '::text) AS payment_method,
            string_agg(DISTINCT origin_pm.name::text, ', '::text) AS payment_method_name,
            string_agg(DISTINCT origin_b.short_name::text, ', '::text) AS payment_bank_name,
            string_agg(DISTINCT pl_1.reference_number::text, ', '::text) AS payment_reference
           FROM payment_lines pl_1
             LEFT JOIN payment_methods origin_pm ON origin_pm.payment_methods_id = pl_1.payment_type_id
             LEFT JOIN banks origin_b ON origin_b.banks_id = pl_1.bank_id
          WHERE pl_1.payment_id = p.payments_id) origin_line ON true
     LEFT JOIN memberships ms ON p.membership_id = ms.memberships_id
     LEFT JOIN plans pl ON ms.plan_id = pl.plans_id
     LEFT JOIN payments payout_p ON payout_p.payments_id = c.payout_payment_id
     LEFT JOIN LATERAL ( SELECT string_agg(DISTINCT payout_pm.code::text, ', '::text) AS payout_method_code,
            string_agg(DISTINCT payout_pm.name::text, ', '::text) AS payout_method_name,
            string_agg(DISTINCT payout_b.short_name::text, ', '::text) AS payout_bank_name,
            string_agg(DISTINCT pol.reference_number::text, ', '::text) AS reference_number
           FROM payment_lines pol
             LEFT JOIN payment_methods payout_pm ON payout_pm.payment_methods_id = pol.payment_type_id
             LEFT JOIN banks payout_b ON payout_b.banks_id = pol.bank_id
          WHERE pol.payment_id = payout_p.payments_id) payout_line ON true
  WHERE c.is_active = true;
```

### v_report_payments

```sql
 SELECT p.payments_id,
    p.uuid AS payment_uuid,
    p.direction,
    p.payment_date,
    p.received_at,
    p.status AS payment_status,
    pc.payment_categories_id,
    pc.code AS payment_category_code,
    pc.name AS payment_category_name,
    pc.direction AS payment_category_direction,
    COALESCE(pc.name,
        CASE
            WHEN p.inscription THEN 'Inscripción'::text
            ELSE 'Mensualidad'::text
        END::character varying) AS concept_label,
    pline.payment_line_id,
    pline.uuid AS payment_line_uuid,
    pm.payment_methods_id,
    pm.code AS payment_method,
    pm.name AS payment_method_name,
    b.banks_id,
    b.code AS bank_code,
    b.short_name AS bank_name,
    pline.reference_number,
    pline.amount AS line_amount,
    pline.status AS line_status,
    COALESCE(pline.amount, p.amount) AS gross_amount,
    cur.code AS currency,
    cur.symbol AS currency_symbol,
        CASE
            WHEN p.amount > 0::numeric AND p.discount_amount IS NOT NULL THEN round(p.discount_amount * (COALESCE(pline.amount, p.amount) / p.amount), 2)
            ELSE 0.00
        END AS discount_amount,
    p.discount_reason,
    COALESCE(pline.amount, p.amount) -
        CASE
            WHEN p.amount > 0::numeric AND p.discount_amount IS NOT NULL THEN round(p.discount_amount * (COALESCE(pline.amount, p.amount) / p.amount), 2)
            ELSE 0.00
        END AS net_amount,
    p.exchange_rate_used,
    p.exchange_rate_date,
    p.inscription,
    p.applied_period,
    p.admin_notes,
    p.reviewed_at,
    p.review_reason,
    p.support_file_name,
    p.created_at AS payment_created_at,
    COALESCE(ru_p.full_name, 'Sin Revisar'::character varying) AS reviewer_name,
    du_p.full_name AS discounter_name,
    per.persons_id,
    per.uuid AS person_uuid,
    per.full_name AS person_name,
    (per.document_type::text || '-'::text) || per.document_number::text AS person_document,
    COALESCE(per.phone, 'N/A'::character varying) AS person_phone,
    COALESCE(per.email::text, 'N/A'::text) AS person_email,
    pr.promoters_id,
    pr.uuid AS promoter_uuid,
    COALESCE(pr.display_name, 'Directo / Sin Promotor'::character varying) AS promoter_name,
    pr.referral_code AS promoter_referral_code,
    ms.memberships_id,
    ms.uuid AS membership_uuid,
    ms.status AS membership_status,
    ms.monthly_fee AS plan_monthly_fee,
    pl.plans_id,
    pl.code AS plan_code,
    pl.name AS plan_name,
    pl.type AS plan_type,
    m.members_id,
    m.uuid AS member_uuid,
    m_p.full_name AS member_name,
    (m_p.document_type::text || '-'::text) || m_p.document_number::text AS member_document,
    COALESCE(m_p.phone, 'N/A'::character varying) AS member_phone,
    COALESCE(m_p.email::text, 'N/A'::text) AS member_email
   FROM payments p
     JOIN currencies cur ON p.currency_id = cur.currencies_id
     JOIN payment_categories pc ON p.payment_type_id = pc.payment_categories_id
     LEFT JOIN payment_lines pline ON pline.payment_id = p.payments_id
     LEFT JOIN payment_methods pm ON pm.payment_methods_id = pline.payment_type_id
     LEFT JOIN banks b ON b.banks_id = pline.bank_id
     LEFT JOIN persons per ON p.person_id = per.persons_id
     LEFT JOIN promoters pr ON p.promoter_id = pr.promoters_id
     LEFT JOIN memberships ms ON p.membership_id = ms.memberships_id
     LEFT JOIN plans pl ON ms.plan_id = pl.plans_id
     LEFT JOIN members m ON ms.member_id = m.members_id
     LEFT JOIN persons m_p ON m.person_id = m_p.persons_id
     LEFT JOIN users ru ON p.reviewed_by = ru.users_id
     LEFT JOIN persons ru_p ON ru.person_id = ru_p.persons_id
     LEFT JOIN users du ON p.discounted_by = du.users_id
     LEFT JOIN persons du_p ON du.person_id = du_p.persons_id
  WHERE p.is_active = true;
```

# Functions

- `bcrypt_hash(plain text, strength integer)` → text [sql]
- `enforce_exclusive_product_owner()` → trigger [plpgsql]
- `grant_permission_to_system()` → trigger [plpgsql]
- `immutable_unaccent(text)` → text [sql]
- `set_membership_contract_number()` → trigger [plpgsql]
- `set_updated_at()` → trigger [plpgsql]
