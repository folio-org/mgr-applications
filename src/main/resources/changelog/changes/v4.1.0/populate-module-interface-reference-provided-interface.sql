-- Copies each provided interface from module.descriptor.provides to the matching PROVIDES row of
-- module_interface_reference. PROVIDES rows that have no matching element stay NULL and are skipped by the
-- egress bootstrap query.
UPDATE module_interface_reference r
   SET provided_interface = p.value
  FROM module m
  CROSS JOIN LATERAL jsonb_array_elements(
    CASE WHEN jsonb_typeof(m.descriptor -> 'provides') = 'array'
         THEN m.descriptor -> 'provides'
         ELSE CAST('[]' AS jsonb)
    END) AS p(value)
 WHERE r.module_id = m.id
   AND r.type = 'PROVIDES'
   AND p.value ->> 'id' = r.id;
