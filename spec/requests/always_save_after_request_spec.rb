require 'spec_helper'

describe "ALWAYS_SAVE_AFTER_REQUEST" do

  before :each do
    get("#{SETTINGS_PATH}/sessionPersistPolicies")
    @oldSessionPersistPoliciesValue = json['value']
    enums = @oldSessionPersistPoliciesValue.split(',')
    enums << 'ALWAYS_SAVE_AFTER_REQUEST'
    post("#{SETTINGS_PATH}/sessionPersistPolicies", body: {value: enums.join(',')})
  end

  after :each do
    post("#{SETTINGS_PATH}/sessionPersistPolicies", body: {value: @oldSessionPersistPoliciesValue})
  end

  it 'should optionally support persisting the session after every request regardless of changed status' do
    post(SESSION_PATH, body: {param2: '5'})
    get("#{SESSION_ATTRIBUTES_PATH}/param2")
    json['value'].should == '5'

    # This is not a perfect guarantee, but in general we're assuming
    # that the requests will happen in the following order:
    # - Post(value=5) starts
    # - Post(value=6) starts
    # - Post(value=6) finishes
    # - Get() returns 6
    # - Post(value=5) finishes
    # - Get() returns 5 (because the change value=5 saved on request finish even though it wasn't a change)
    long_request = Thread.new do
      post("#{SESSION_ATTRIBUTES_PATH}/param2", body: {value: '5', sleep: 2000})
    end

    sleep 0.5
    get("#{SESSION_ATTRIBUTES_PATH}/param2")
    json['value'].should == '5'

    post("#{SESSION_ATTRIBUTES_PATH}/param2", body: {value: '6'})
    get("#{SESSION_ATTRIBUTES_PATH}/param2")
    json['value'].should == '6'

    long_request.join

    get("#{SESSION_ATTRIBUTES_PATH}/param2")
    json['value'].should == '5'
  end
end
