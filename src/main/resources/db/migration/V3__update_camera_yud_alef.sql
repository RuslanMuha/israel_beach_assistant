-- Public beach webcam landing page (HEAD checks expect HTTP 200 from the page).
UPDATE camera_endpoint
SET live_url = 'https://www.camguide.net/asia/israel/ashdod/yud-alef-beach/'
WHERE beach_id = (SELECT id FROM beach WHERE slug = 'yud-alef');
