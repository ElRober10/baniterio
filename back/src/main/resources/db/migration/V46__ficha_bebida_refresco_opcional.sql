-- El refresco deja de ser obligatorio: hay gente que solo bebe alcohol (o
-- nada) y no quiere elegir ningun refresco para alternar.
ALTER TABLE ficha_bebida ALTER COLUMN refresco_bebida_id DROP NOT NULL;
