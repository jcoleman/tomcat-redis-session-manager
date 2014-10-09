require 'spec_helper'

describe 'session creation' do

  it 'should begin without a session' do
    get(SESSION_PATH)
    json.should_not have_key('sessionId')
  end

  it 'should generate a session ID' do
    post(SESSION_PATH)
    json.should have_key('sessionId')
  end

  it 'should not create a session when requesting session creation with existing session ID' do
    pending
  end

  it 'should detect a session ID collision and generate a new session ID' do
    pending
  end

  it 'should detect and report race conditions when creating new sessions' do
    pending
  end
end
