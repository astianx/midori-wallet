-- Preserve the current binding and all historical alias reservations.
INSERT INTO tag_reservations(alias,wallet_id)
SELECT alias,wallet_id FROM wallet_tags
ON CONFLICT (alias) DO NOTHING;

ALTER TABLE tag_reservations ADD CONSTRAINT tag_reservations_alias_wallet_key UNIQUE(alias,wallet_id);
ALTER TABLE wallet_tags ADD CONSTRAINT wallet_tags_reservation_owner_fkey
  FOREIGN KEY(alias,wallet_id) REFERENCES tag_reservations(alias,wallet_id);

CREATE FUNCTION protect_tag_binding() RETURNS trigger
LANGUAGE plpgsql SET search_path=pg_catalog AS $$
BEGIN
  IF TG_OP = 'DELETE' OR TG_OP = 'TRUNCATE' THEN
    RAISE EXCEPTION 'Tag identity cannot be removed' USING ERRCODE='23514',CONSTRAINT='wallet_tag_immutable';
  END IF;
  IF ROW(NEW.alias,NEW.wallet_id,NEW.account_id,NEW.revision)
    IS DISTINCT FROM ROW(OLD.alias,OLD.wallet_id,OLD.account_id,OLD.revision) THEN
    RAISE EXCEPTION 'Tag identity cannot be changed' USING ERRCODE='23514',CONSTRAINT='wallet_tag_immutable';
  END IF;
  RETURN NEW;
END;
$$;
CREATE TRIGGER wallet_tags_permanent BEFORE UPDATE OR DELETE ON wallet_tags
FOR EACH ROW EXECUTE FUNCTION protect_tag_binding();
CREATE TRIGGER wallet_tags_no_truncate BEFORE TRUNCATE ON wallet_tags
FOR EACH STATEMENT EXECUTE FUNCTION protect_tag_binding();

CREATE FUNCTION protect_tag_reservation() RETURNS trigger
LANGUAGE plpgsql SET search_path=pg_catalog AS $$
BEGIN
  IF TG_OP = 'DELETE' OR TG_OP = 'TRUNCATE' THEN
    RAISE EXCEPTION 'A registered alias cannot be released' USING ERRCODE='23514',CONSTRAINT='tag_reservation_immutable';
  END IF;
  IF NEW IS DISTINCT FROM OLD THEN
    RAISE EXCEPTION 'A registered alias cannot be reassigned' USING ERRCODE='23514',CONSTRAINT='tag_reservation_immutable';
  END IF;
  RETURN NEW;
END;
$$;
CREATE TRIGGER tag_reservations_permanent BEFORE UPDATE OR DELETE ON tag_reservations
FOR EACH ROW EXECUTE FUNCTION protect_tag_reservation();
CREATE TRIGGER tag_reservations_no_truncate BEFORE TRUNCATE ON tag_reservations
FOR EACH STATEMENT EXECUTE FUNCTION protect_tag_reservation();
