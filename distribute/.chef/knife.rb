serengeti_home = ENV["SERENGETI_HOME"] || "/opt/serengeti"

## chef workstation ##
log_level                :info
log_location             STDOUT
node_name                "serengeti"
client_key               "#{serengeti_home}/.chef/serengeti.pem"
validation_client_name   "chef-validator"
validation_key           "#{serengeti_home}/.chef/validation.pem"
chef_server_url          "http://localhost:4000"
cache_type               "BasicFile"
cache_options( :path =>  "#{serengeti_home}/.chef/checksums" )

current_dir = File.dirname(__FILE__)

###### cluster_chef ######

# The list of paths holding clusters
cluster_path      [ File.expand_path("#{serengeti_home}/tmp/.ironfan-clusters") ]
# The directory holding your cloud keypairs
keypair_path      File.expand_path(current_dir)

# The first things have lowest priority (so, site-cookbooks gets to win)
cookbook_path            [
  "#{serengeti_home}/cookbooks/cookbooks",
]

# Configure Bootstrap
knife[:bootstrap_runs_chef_client] = true
bootstrap_chef_version   "~> 0.10.0"
knife[:ssh_user] = "serengeti"
knife[:ssh_password] = "password"
