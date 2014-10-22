require 'httparty'

module RequestsHelper

  class ExampleAppClient
    include ::HTTParty
    base_uri 'http://172.28.128.3:8080/example'
  end

  def client
    @client ||= ExampleAppClient.new
  end

  def get(path, options={})
    send_request(:get, path, options)
  end

  def put(path, options={})
    send_request(:put, path, options)
  end

  def post(path, options={})
    send_request(:post, path, options)
  end

  def delete(path, options={})
    send_request(:delete, path, options)
  end

  def send_request(method, path, options={})
    options ||= {}
    headers = options[:headers] || {}
    if cookie && !options.key('Cookie')
      headers['Cookie'] = cookie
    end
    options = options.merge(headers: headers)
    self.response = self.client.class.send(method, path, options)
  end

  def request
    response.request
  end

  def response
    @response
  end

  def response=(r)
    if r
      if r.headers.key?('Set-Cookie')
        @cookie = r.headers['Set-Cookie']
      end
    else
      @cookie = nil
    end
    @json = nil
    @response = r
  end

  def cookie
    @cookie
  end

  def json
    @json ||= JSON.parse(response.body)
  end
end
