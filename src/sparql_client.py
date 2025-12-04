import urllib.request
import urllib.parse
import json
import sys

class FusekiClient:
    def __init__(self, fuseki_url: str):
        self.fuseki_url = fuseki_url

    def query(self, sparql_query: str):
        """
        Sends a SPARQL query to the specified Fuseki server and returns the JSON results.
        """
        try:
            # Prepare the request data
            data = urllib.parse.urlencode({'query': sparql_query}).encode('utf-8')

            # Create a request object
            req = urllib.request.Request(self.fuseki_url, data=data, headers={
                'Content-Type': 'application/x-www-form-urlencoded',
                'Accept': 'application/sparql-results+json' # Request JSON results
            })

            # Send the request and get the response
            with urllib.request.urlopen(req) as response:
                response_data = response.read().decode('utf-8')
                content_type = response.getheader('Content-Type')
                
                # Attempt to parse and return JSON response
                if 'application/sparql-results+json' in content_type or 'application/json' in content_type:
                    try:
                        return json.loads(response_data)
                    except json.JSONDecodeError:
                        print("Warning: Could not decode JSON response.")
                        return {"error": "JSONDecodeError", "raw_response": response_data}
                else:
                    print(f"Warning: Unexpected Content-Type: {content_type}")
                    return {"error": "UnexpectedContentType", "raw_response": response_data}

        except urllib.error.URLError as e:
            print(f"Error connecting to Fuseki server: {e.reason}")
            print(f"Please ensure Fuseki is running at {self.fuseki_url}")
            return {"error": "URLError", "reason": str(e.reason)}
        except Exception as e:
            print(f"An unexpected error occurred: {e}")
            return {"error": "Exception", "reason": str(e)}

# --- Configuration ---
FUSEKI_URL = "http://localhost:3030/MetaCognition/query"

# --- SPARQL Query ---
SPARQL_QUERY = """
SELECT ?s ?p ?o
WHERE {
  ?s ?p ?o .
}
LIMIT 10
"""

if __name__ == "__main__":
    url_to_use = FUSEKI_URL
    if len(sys.argv) > 1:
        url_to_use = sys.argv[1]

    client = FusekiClient(url_to_use)
    print(f"Sending SPARQL query to {url_to_use}...")
    print("--- Query ---")
    print(SPARQL_QUERY)
    print("-------------")
    results = client.query(SPARQL_QUERY)
    
    if results:
        print("\n--- Results ---")
        print(json.dumps(results, indent=2))
    else:
        print("\n--- No results or an error occurred ---")
