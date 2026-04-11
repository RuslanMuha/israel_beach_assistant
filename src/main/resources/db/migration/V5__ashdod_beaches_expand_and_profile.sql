-- Structured display metadata (optional JSON per beach)
ALTER TABLE beach
    ADD COLUMN IF NOT EXISTS profile_json TEXT;

-- Enrich existing Ashdod beaches (UTF-8; standard quoted strings for H2 + PostgreSQL)
UPDATE beach
SET profile_json = '{"description":"Городской пляж с инфраструктурой; популярен у местных и туристов.","categories":["family","urban"],"facilities":{"showers":true,"toilets":true,"playground":false,"sportsFacilities":true,"accessible":true,"parking":true},"accessibilityNotes":"На ряде участков есть доступ к воде для маломобильных гостей (уточняйте на месте).","parkingNotes":"Парковка в пляжной зоне (по правилам города).","notes":"Камера и спасатели в сезон.","lifeguardNotes":"Сезон и часы — команда /hours.","waterQualityPlaceholder":"Лабораторные данные по качеству воды не подключены.","jellyfishPlaceholder":"Актуальность — команда /jellyfish."}'
WHERE slug = 'yud-alef';

UPDATE beach
SET profile_json = '{"description":"Пляж у сосновой рощи (Ораним); семейная атмосфера.","categories":["family","nature"],"facilities":{"showers":true,"toilets":true,"playground":true,"sportsFacilities":false,"accessible":true,"parking":true},"accessibilityNotes":"","parkingNotes":"Парковка у пляжной зоны.","notes":"","lifeguardNotes":"Сезон и часы — /hours.","waterQualityPlaceholder":"Лабораторные данные по качеству воды не подключены.","jellyfishPlaceholder":"Актуальность — /jellyfish."}'
WHERE slug = 'oranim';

UPDATE beach
SET profile_json = '{"description":"Центральный пляж Лидо; развитая инфраструктура.","categories":["family","urban"],"facilities":{"showers":true,"toilets":true,"playground":false,"sportsFacilities":true,"accessible":true,"parking":true},"accessibilityNotes":"","parkingNotes":"","notes":"","lifeguardNotes":"Сезон и часы — /hours.","waterQualityPlaceholder":"Лабораторные данные по качеству воды не подключены.","jellyfishPlaceholder":"Актуальность — /jellyfish."}'
WHERE slug = 'lido';

UPDATE beach
SET profile_json = '{"description":"Участок побережья; спасательный пост может отсутствовать вне сезона.","categories":["urban"],"facilities":{"showers":false,"toilets":true,"playground":false,"sportsFacilities":false,"accessible":false,"parking":true},"accessibilityNotes":"","parkingNotes":"","notes":"Проверяйте наличие спасателей на месте.","lifeguardNotes":"В данных приложения спасатели не отмечены; уточняйте у муниципалитета.","waterQualityPlaceholder":"Лабораторные данные по качеству воды не подключены.","jellyfishPlaceholder":"Актуальность — /jellyfish."}'
WHERE slug = 'dolfin';

UPDATE beach
SET profile_json = '{"description":"Северный участок набережной Ашдода.","categories":["urban"],"facilities":{"showers":false,"toilets":true,"playground":false,"sportsFacilities":false,"accessible":false,"parking":true},"accessibilityNotes":"","parkingNotes":"","notes":"","lifeguardNotes":"В данных приложения спасатели не отмечены; уточняйте на месте.","waterQualityPlaceholder":"Лабораторные данные по качеству воды не подключены.","jellyfishPlaceholder":"Актуальность — /jellyfish."}'
WHERE slug = 'north-beach';

INSERT INTO beach (city_id, display_name, slug, latitude, longitude,
                   is_active, supports_swimming, has_lifeguards, has_camera, has_jellyfish_source, profile_json)
SELECT c.id,
       'Mei Ami',
       'mei-ami',
       31.7910,
       34.6485,
       TRUE,
       TRUE,
       TRUE,
       FALSE,
       TRUE,
       '{"description":"Объявленный пляж купания; часть «золотой» линии Ашдода.","categories":["family"],"facilities":{"showers":true,"toilets":true,"playground":false,"sportsFacilities":false,"accessible":true,"parking":true},"accessibilityNotes":"","parkingNotes":"Парковка у пляжной зоны.","notes":"","lifeguardNotes":"Сезон и часы — /hours.","waterQualityPlaceholder":"Лабораторные данные по качеству воды не подключены.","jellyfishPlaceholder":"Актуальность — /jellyfish."}'
FROM city c
WHERE c.name = 'Ashdod'
LIMIT 1;

INSERT INTO beach (city_id, display_name, slug, latitude, longitude,
                   is_active, supports_swimming, has_lifeguards, has_camera, has_jellyfish_source, profile_json)
SELECT c.id,
       'Hakshatot',
       'hakshatot',
       31.8030,
       34.6500,
       TRUE,
       TRUE,
       TRUE,
       FALSE,
       TRUE,
       '{"description":"Пляж «Каменные арки»; популярен для прогулок и купания.","categories":["family","scenic"],"facilities":{"showers":true,"toilets":true,"playground":false,"sportsFacilities":false,"accessible":true,"parking":true},"accessibilityNotes":"","parkingNotes":"","notes":"","lifeguardNotes":"Сезон и часы — /hours.","waterQualityPlaceholder":"Лабораторные данные по качеству воды не подключены.","jellyfishPlaceholder":"Актуальность — /jellyfish."}'
FROM city c
WHERE c.name = 'Ashdod'
LIMIT 1;

INSERT INTO beach (city_id, display_name, slug, latitude, longitude,
                   is_active, supports_swimming, has_lifeguards, has_camera, has_jellyfish_source, profile_json)
SELECT c.id,
       'Marina',
       'marina',
       31.8070,
       34.6525,
       TRUE,
       TRUE,
       TRUE,
       FALSE,
       TRUE,
       '{"description":"Пляж у марины; набережная и сервисы для отдыхающих.","categories":["family","urban"],"facilities":{"showers":true,"toilets":true,"playground":false,"sportsFacilities":true,"accessible":true,"parking":true},"accessibilityNotes":"","parkingNotes":"","notes":"","lifeguardNotes":"Сезон и часы — /hours.","waterQualityPlaceholder":"Лабораторные данные по качеству воды не подключены.","jellyfishPlaceholder":"Актуальность — /jellyfish."}'
FROM city c
WHERE c.name = 'Ashdod'
LIMIT 1;

INSERT INTO beach (city_id, display_name, slug, latitude, longitude,
                   is_active, supports_swimming, has_lifeguards, has_camera, has_jellyfish_source, profile_json)
SELECT c.id,
       'Separate',
       'separate-beach',
       31.8110,
       34.6535,
       TRUE,
       TRUE,
       TRUE,
       FALSE,
       TRUE,
       '{"description":"Отдельный пляж с раздельным купанием; религиозная аудитория.","categories":["religious","separate"],"facilities":{"showers":true,"toilets":true,"playground":false,"sportsFacilities":false,"accessible":true,"parking":true},"accessibilityNotes":"Режим и правила — на месте.","parkingNotes":"","notes":"Уважайте правила раздельного пляжа.","lifeguardNotes":"Сезон и часы — /hours.","waterQualityPlaceholder":"Лабораторные данные по качеству воды не подключены.","jellyfishPlaceholder":"Актуальность — /jellyfish."}'
FROM city c
WHERE c.name = 'Ashdod'
LIMIT 1;

INSERT INTO beach (city_id, display_name, slug, latitude, longitude,
                   is_active, supports_swimming, has_lifeguards, has_camera, has_jellyfish_source, profile_json)
SELECT c.id,
       'Gil',
       'gil',
       31.8165,
       34.6545,
       TRUE,
       TRUE,
       TRUE,
       FALSE,
       TRUE,
       '{"description":"Пляж для водных видов спорта; усиленный ветер и снаряжение на площадке.","categories":["sport","youth"],"facilities":{"showers":true,"toilets":true,"playground":false,"sportsFacilities":true,"accessible":false,"parking":true},"accessibilityNotes":"","parkingNotes":"","notes":"Оценивайте свой уровень и снаряжение.","lifeguardNotes":"Сезон и часы — /hours.","waterQualityPlaceholder":"Лабораторные данные по качеству воды не подключены.","jellyfishPlaceholder":"Актуальность — /jellyfish."}'
FROM city c
WHERE c.name = 'Ashdod'
LIMIT 1;

INSERT INTO beach (city_id, display_name, slug, latitude, longitude,
                   is_active, supports_swimming, has_lifeguards, has_camera, has_jellyfish_source, profile_json)
SELECT c.id,
       'Gandhi',
       'gandhi',
       31.8185,
       34.6555,
       TRUE,
       TRUE,
       TRUE,
       FALSE,
       TRUE,
       '{"description":"Пляж для водных видов спорта и активного отдыха.","categories":["sport","youth"],"facilities":{"showers":true,"toilets":true,"playground":false,"sportsFacilities":true,"accessible":false,"parking":true},"accessibilityNotes":"","parkingNotes":"","notes":"","lifeguardNotes":"Сезон и часы — /hours.","waterQualityPlaceholder":"Лабораторные данные по качеству воды не подключены.","jellyfishPlaceholder":"Актуальность — /jellyfish."}'
FROM city c
WHERE c.name = 'Ashdod'
LIMIT 1;

INSERT INTO beach_alias (beach_id, alias)
SELECT b.id, a.alias
FROM beach b
         CROSS JOIN (VALUES ('mei ami'), ('מי עמי'), ('מי-עמי'), ('мей ами')) AS a(alias)
WHERE b.slug = 'mei-ami';

INSERT INTO beach_alias (beach_id, alias)
SELECT b.id, a.alias
FROM beach b
         CROSS JOIN (VALUES ('hakshatot'), ('arches'), ('каменные арки'), ('הקשתות')) AS a(alias)
WHERE b.slug = 'hakshatot';

INSERT INTO beach_alias (beach_id, alias)
SELECT b.id, a.alias
FROM beach b
         CROSS JOIN (VALUES ('marina beach'), ('מרינה'), ('марина')) AS a(alias)
WHERE b.slug = 'marina';

INSERT INTO beach_alias (beach_id, alias)
SELECT b.id, a.alias
FROM beach b
         CROSS JOIN (VALUES ('separate'), ('religious beach'), ('נפרד'), ('החוף הנפרד'), ('отдельный')) AS a(alias)
WHERE b.slug = 'separate-beach';

INSERT INTO beach_alias (beach_id, alias)
SELECT b.id, a.alias
FROM beach b
         CROSS JOIN (VALUES ('gil beach'), ('חוף גיל'), ('гиль')) AS a(alias)
WHERE b.slug = 'gil';

INSERT INTO beach_alias (beach_id, alias)
SELECT b.id, a.alias
FROM beach b
         CROSS JOIN (VALUES ('gandhi beach'), ('חוף גנדי'), ('ганди')) AS a(alias)
WHERE b.slug = 'gandhi';

INSERT INTO lifeguard_schedule (beach_id, schedule_type, day_of_week, open_time, close_time, is_active, source_type,
                                captured_at)
SELECT b.id, 'REGULAR', dow, '09:00', '18:00', TRUE, 'LIFEGUARD_SCHEDULE', CURRENT_TIMESTAMP
FROM beach b
         CROSS JOIN (VALUES (1), (2), (3), (4), (5), (6), (7)) AS days (dow)
WHERE b.slug IN ('mei-ami', 'hakshatot', 'marina', 'separate-beach', 'gil', 'gandhi')
  AND b.has_lifeguards = TRUE;
