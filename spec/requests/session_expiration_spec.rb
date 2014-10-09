require 'spec_helper'

describe 'session expiration' do

  before :each do
    get("#{SETTINGS_PATH}/maxInactiveInterval")
    @oldMaxInactiveIntervalValue = json['value']
    post("#{SETTINGS_PATH}/maxInactiveInterval", body: {value: '1'})
  end

  after :each do
    post("#{SETTINGS_PATH}/maxInactiveInterval", body: {value: @oldMaxInactiveIntervalValue})
  end

  it 'should no longer contain a session after the expiration timeout has passed' do
    post(SESSION_PATH)
    created_session_id = json['sessionId']
    get(SESSION_PATH)
    json['sessionId'].should == created_session_id
    sleep 1.0
    get(SESSION_PATH)
    json['sessionId'].should be_nil
  end
end
