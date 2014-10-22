# encoding: utf-8

require 'bundler'
require 'bundler/setup'
require 'berkshelf/thor'

begin
  require 'kitchen/thor_tasks'
  Kitchen::ThorTasks.new
rescue LoadError
  puts ">>>>> Kitchen gem not loaded, omitting tasks" unless ENV['CI']
end
