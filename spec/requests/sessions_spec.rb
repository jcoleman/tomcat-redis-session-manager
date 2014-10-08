require 'spec_helper'

SESSION_PATH = '/session'
SESSIONS_PATH = '/sessions'
SESSION_ATTRIBUTES_PATH = '/session/attributes'
SETTINGS_PATH = '/settings'

describe "Tomcat Redis Sessions", type: :controller do

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

  describe 'session updating' do
    it 'should support setting a value in the session' do
      post(SESSION_PATH, body: {param1: '5'})
      json['attributes'].should have_key('param1')
      json['attributes']['param1'].should == '5'
    end

    it 'should support updating a value in the session' do
      post(SESSION_PATH, body: {param1: '5'})
      json['attributes'].should have_key('param1')
      json['attributes']['param1'].should == '5'

      put(SESSION_PATH, query: {param1: '6'})
      json['attributes']['param1'].should == '6'
    end

    it 'should support setting a complex values in the session' do
      post(SESSION_PATH, body: {param1: {subparam: '5'}})
      json['attributes']['param1'].should have_key('subparam')
      json['attributes']['param1']['subparam'].should == '5'
    end

    it 'should persist session attributes between requests' do
      post(SESSION_PATH, body: {param1: '5'})
      get(SESSION_PATH)
      json['attributes']['param1'].should == '5'
    end

    it 'should persist updated session attributes between requests' do
      post(SESSION_PATH, body: {param1: '5'})
      put(SESSION_PATH, query: {param1: '6'})
      get(SESSION_PATH)
      json['attributes']['param1'].should == '6'
    end

    it 'should support removing a value in the session' do
      post("#{SESSION_ATTRIBUTES_PATH}/param1", body: {value: '5'})
      get(SESSION_ATTRIBUTES_PATH)
      json['keys'].should include('param1')

      delete("#{SESSION_ATTRIBUTES_PATH}/param1")
      get(SESSION_ATTRIBUTES_PATH)
      json['keys'].should_not include('param1')
    end

    it 'should only update if the session changes' do
      post(SESSION_PATH, body: {param1: '5'})

      # This is not a perfect guarantee, but in general we're assuming
      # that the requests will happen in the following order:
      # - Post(value=5) starts
      # - Post(value=6) starts
      # - Post(value=6) finishes
      # - Get() returns 6
      # - Post(value=5) finishes
      #     Note: this would represent a change from the current persisted value
      #           but it does not represent a change from when this request's
      #           copy of the session was loaded, so it shouldn't be persisted.
      # - Get() returns 6
      last_request_to_finish = Thread.new do
        post("#{SESSION_ATTRIBUTES_PATH}/param1", body: {value: '5', sleep: 2000})
      end
      sleep 1
      post("#{SESSION_ATTRIBUTES_PATH}/param1", body: {value: '6'})
      # Verify our assumption that this request loaded it's session
      # before the request Post(value=7) finished.
      json['oldValue'].should == '5'
      get("#{SESSION_ATTRIBUTES_PATH}/param1")
      json['value'].should == '6'

      last_request_to_finish.join

      get("#{SESSION_ATTRIBUTES_PATH}/param1")
      json['value'].should == '6'
    end

    it 'should default to last-write-wins behavior for simultaneous updates' do
      post(SESSION_PATH, body: {param1: '5'})

      # This is not a perfect guarantee, but in general we're assuming
      # that the requests will happen in the following order:
      # - Post(value=7) starts
      # - Post(value=6) starts
      # - Post(value=6) finishes
      # - Get() returns 6
      # - Post(value=7) finishes
      # - Get() returns 7
      winner = Thread.new do
        post("#{SESSION_ATTRIBUTES_PATH}/param1", body: {value: '7', sleep: 2000})
      end
      sleep 1
      post("#{SESSION_ATTRIBUTES_PATH}/param1", body: {value: '6'})
      # Verify our assumption that this request loaded it's session
      # before the request Post(value=7) finished.
      json['oldValue'].should == '5'
      get("#{SESSION_ATTRIBUTES_PATH}/param1")
      json['value'].should == '6'

      winner.join

      get("#{SESSION_ATTRIBUTES_PATH}/param1")
      json['value'].should == '7'
    end

    describe "change on save" do

      before :each do
        get("#{SETTINGS_PATH}/sessionPersistPolicies")
        @oldSessionPersistPoliciesValue = json['value']
        enums = @oldSessionPersistPoliciesValue.split(',')
        enums << 'SAVE_ON_CHANGE'
        post("#{SETTINGS_PATH}/sessionPersistPolicies", body: {value: enums.join(',')})
      end

      after :each do
        post("#{SETTINGS_PATH}/sessionPersistPolicies", body: {value: @oldSessionPersistPoliciesValue})
      end

      it 'should support persisting the session on change to minimize race conditions on simultaneous updates' do
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
        # - Get() returns 6 (because the change value=5 saved immediately rather than on request finish)
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
        json['value'].should == '6'
      end
    end
  end

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

end
