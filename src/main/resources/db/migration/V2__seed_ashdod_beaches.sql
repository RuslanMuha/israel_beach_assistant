-- Seed: Ashdod city
INSERT INTO city (name, country, timezone)
VALUES ('Ashdod', 'Israel', 'Asia/Jerusalem');

-- Seed: Ashdod beaches
INSERT INTO beach (city_id, display_name, slug, latitude, longitude,
                   is_active, supports_swimming, has_lifeguards, has_camera, has_jellyfish_source)
VALUES
    (1, 'Yud Alef', 'yud-alef',      31.7949, 34.6492, TRUE, TRUE, TRUE, TRUE, TRUE),
    (1, 'Oranim',   'oranim',         31.8010, 34.6450, TRUE, TRUE, TRUE, FALSE, TRUE),
    (1, 'Lido',     'lido',           31.8100, 34.6520, TRUE, TRUE, TRUE, FALSE, FALSE),
    (1, 'Dolfin',   'dolfin',         31.8150, 34.6540, TRUE, TRUE, FALSE, FALSE, FALSE),
    (1, 'Haof Hatzafoni', 'north-beach', 31.8200, 34.6560, TRUE, TRUE, FALSE, FALSE, FALSE);

-- Aliases: Yud Alef
INSERT INTO beach_alias (beach_id, alias) VALUES
    (1, 'yud alef'),
    (1, 'יא'),
    (1, 'יוד אלף'),
    (1, '11'),
    (1, 'пляж 11'),
    (1, 'юд алеф');

-- Aliases: Oranim
INSERT INTO beach_alias (beach_id, alias) VALUES
    (2, 'oranim'),
    (2, 'аранім'),
    (2, 'оранім'),
    (2, 'ораним');

-- Aliases: Lido
INSERT INTO beach_alias (beach_id, alias) VALUES
    (3, 'lido'),
    (3, 'лидо');

-- Aliases: Dolfin
INSERT INTO beach_alias (beach_id, alias) VALUES
    (4, 'dolfin'),
    (4, 'dolphin'),
    (4, 'дельфин'),
    (4, 'долфин');

-- Aliases: North beach
INSERT INTO beach_alias (beach_id, alias) VALUES
    (5, 'north beach'),
    (5, 'northern beach'),
    (5,  'северный пляж'),
    (5, 'hof hatzafoni');

-- Lifeguard regular schedules (all 7 days, summer hours)
INSERT INTO lifeguard_schedule (beach_id, schedule_type, day_of_week, open_time, close_time, is_active, source_type, captured_at)
SELECT b.id, 'REGULAR', dow, '09:00', '18:00', TRUE, 'LIFEGUARD_SCHEDULE', NOW()
FROM beach b
         CROSS JOIN (VALUES (1),(2),(3),(4),(5),(6),(7)) AS days(dow)
WHERE b.has_lifeguards = TRUE;

-- Camera endpoint: Yud Alef only for now (placeholder URL)
INSERT INTO camera_endpoint (beach_id, provider_name, live_url, snapshot_url, is_active, health_status)
VALUES (1, 'BeachCam IL', 'https://example.com/cam/yud-alef', NULL, TRUE, 'UNKNOWN');
