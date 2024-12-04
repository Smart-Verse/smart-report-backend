CREATE TABLE IF NOT EXISTS user_configuration(

     id uuid,
     name varchar,
     user_photo varchar,
     theme varchar,
     lang varchar,
     email varchar,
     hash uuid
);

ALTER TABLE user_configuration  ADD CONSTRAINT pk_user_configuration  PRIMARY KEY (id);