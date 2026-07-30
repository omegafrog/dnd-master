-- RuntimeBinding.rulebook_ids_json was the retrieval scope before SessionKnowledgeSet.
-- Copy the latest legacy binding once; later writes must use the session scope API.
WITH latest_binding AS (
    SELECT DISTINCT ON (adventure_id)
           adventure_id, rulebook_ids_json
      FROM adventure_runtime_binding
     ORDER BY adventure_id, binding_version DESC
), legacy_scope AS (
    SELECT a.session_id,
           values.ordinality - 1 AS legacy_selection_order,
           values.value::uuid AS knowledge_document_id
      FROM latest_binding binding
      JOIN adventure a ON a.adventure_id = binding.adventure_id
      CROSS JOIN LATERAL jsonb_array_elements_text(
          COALESCE(NULLIF(binding.rulebook_ids_json, ''), '[]')::jsonb) WITH ORDINALITY values(value, ordinality)
     WHERE NOT EXISTS (
         SELECT 1
           FROM adventure_session_knowledge_document existing
          WHERE existing.session_id = a.session_id)
), ordered_scope AS (
    SELECT session_id,
           row_number() OVER (PARTITION BY session_id ORDER BY legacy_selection_order) - 1 AS selection_order,
           knowledge_document_id
      FROM (SELECT DISTINCT ON (session_id, knowledge_document_id)
                   session_id, legacy_selection_order, knowledge_document_id
              FROM legacy_scope
             ORDER BY session_id, knowledge_document_id, legacy_selection_order) distinct_scope
)
INSERT INTO adventure_session_knowledge_document (session_id, selection_order, knowledge_document_id)
SELECT session_id, selection_order, knowledge_document_id
  FROM ordered_scope
ON CONFLICT DO NOTHING;
