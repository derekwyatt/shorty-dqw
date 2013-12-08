drop table if exists hash_to_url;
create table hash_to_url (
  hash varchar(8),
  url varchar(1024),
  primary key (hash)
);
drop table if exists hash_clicks;
create table hash_clicks (
  hash varchar(8),
  stamp timestamp default current_timestamp,
  ipaddr varchar(64),
  primary key (hash, stamp, ipaddr)
);
