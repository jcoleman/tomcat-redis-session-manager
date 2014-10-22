require 'spec_helper'

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

  it 'should detect nested changes and persist them' do
    post(SESSION_PATH, body: {param1: {subparam: '5'}})
    json['attributes']['param1']['subparam'].should == '5'
    post(SESSION_PATH, body: {param1: {subparam: '6'}})
    get(SESSION_PATH)
    json['attributes']['param1']['subparam'].should == '6'
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
end
