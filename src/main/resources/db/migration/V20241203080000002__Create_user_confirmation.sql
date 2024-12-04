CREATE TABLE IF NOT EXISTS user_confirmation(

                                                id uuid,
                                                user_id uuid,
                                                hash varchar
);

ALTER TABLE user_confirmation  ADD CONSTRAINT pk_user_confirmation  PRIMARY KEY (id);