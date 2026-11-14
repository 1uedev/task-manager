# Preparation of the GitLab 16 run (TASK-113), started with "gitlab-rails runner" inside the GitLab container.
# Creates a personal access token of root for the preparation through the API and a trusted OAuth application with the
# scope read_api whose redirect is the callback of the task manager, and prints both as one line of JSON. Everything lives
# only as long as the throw-away container of the run.
require 'json'

callback = ENV.fetch('TASK_MANAGER_CALLBACK')
root = User.find_by_username!('root')
token = root.personal_access_tokens.create!(name: 'task-manager-run', scopes: [:api], expires_at: 2.days.from_now)
application = Doorkeeper::Application.create!(name: 'Task Manager Lauf', redirect_uri: callback, scopes: 'read_api',
  confidential: true, trusted: true)
puts JSON.generate({ token: token.token, app_db_id: application.id, app_id: application.uid,
  app_secret: application.plaintext_secret || application.secret })
