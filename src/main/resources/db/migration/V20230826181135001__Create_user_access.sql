CREATE TABLE IF NOT EXISTS users(
      id uuid,
      name varchar,
      email varchar,
      password varchar,
      tenant varchar,
      active boolean,
      user_confirm boolean
);