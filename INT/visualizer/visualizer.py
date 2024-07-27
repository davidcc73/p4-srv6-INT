import networkx as nx
import matplotlib.pyplot as plt
from matplotlib.patches import Patch
from influxdb import InfluxDBClient
import sys
from datetime import datetime, timedelta
import random

# Configurações do InfluxDB
host = 'localhost'
database = "int"

# Conexão ao InfluxDB
influx_client = InfluxDBClient(host=host, database=database)
if influx_client.ping():  # Verificar se a conexão é bem-sucedida
    print("Connected to InfluxDB successfully.") 
else:
    print("Failed to connect to InfluxDB")
    sys.exit(1)

# Lista de cores para serem usadas
colors = ['red', 'green', 'blue', 'orange', 'purple', 'brown', 'pink', 'gray', 'cyan', 'magenta']

# Dicionário para armazenar cores para cada caminho
path_colors = {}

# Função para atribuir uma cor a um caminho
def assign_color(path):
    if path not in path_colors:
        if len(colors) > 0:
            path_colors[path] = colors.pop(0)
        else:
            # Se as cores predefinidas acabarem, gerar uma cor aleatória
            path_colors[path] = "#{:06x}".format(random.randint(0, 0xFFFFFF))
    return path_colors[path]

# Função para atualizar o gráfico
def update_graph(G, new_data, edge_update_time):
    unique_paths = set()
    
    for point in new_data:
        path = point['path']
        flow_label = point['flow_label']
        src_ip = point['src_ip']
        dst_ip = point['dst_ip']
        
        path_identifier = f"{path}_{flow_label}_{src_ip}_{dst_ip}"  # Create a unique identifier for each path
        unique_paths.add(path_identifier)
    
    for path_identifier in unique_paths:
        path, flow_label, src_ip, dst_ip = path_identifier.split('_')
        
        path_color = assign_color(path_identifier)  # Atribui uma cor ao caminho
        print("\nPath:", path, "Flow Label:", flow_label, "Source IP:", src_ip, "Destination IP:", dst_ip, "Color:", path_color)
        switches = path.split('-')
        
        for i in range(len(switches) - 1):
            egress_switch_id = int(switches[i])
            ingress_switch_id = int(switches[i + 1])
            edge = (egress_switch_id, ingress_switch_id, path_color)  # Include path color in the edge
            if edge not in edge_colors:
                edge_colors[edge] = path_color
            print('Edge:', edge)
            edge_update_time[edge] = datetime.now()  # Atualiza o tempo da última atualização
    
       

# Função para visualizar o gráfico
def visualize_graph(G, edge_colors):
    plt.clf()  # Limpar a figura atual

    # Desenhar todos os nós
    nx.draw_networkx_nodes(G, pos, node_size=400, edgecolors="black")

    # Desenhar todas as arestas em cinza claro
    nx.draw_networkx_edges(G, pos, edgelist=G.edges(), edge_color="lightgray", style="dashed", connectionstyle="arc3,rad=0.0")

    # Desenhar as arestas com cores atribuídas
    for i, (edge, color) in enumerate(edge_colors.items()):
        egress_switch_id, ingress_switch_id, _ = edge
        nx.draw_networkx_edges(
            G, pos, edgelist=[(egress_switch_id, ingress_switch_id)], 
            edge_color=color, arrows=True, 
            connectionstyle=f"arc3,rad={(i % 10) * 0.1 - 0.2}"  # Adjust arc radius for each edge
        )

    # Desenhar rótulos dos nós
    nx.draw_networkx_labels(G, pos, font_size=13, font_family="sans-serif")

    # Criar uma legenda para os fluxos
    legend_elements = []
    for path_identifier, color in path_colors.items():
        path, flow_label, src_ip, dst_ip = path_identifier.split('_')
        legend_label = f"Flow {flow_label} ({src_ip} -> {dst_ip})"
        legend_elements.append(Patch(facecolor=color, edgecolor='black', label=legend_label))

    # Adicionar a legenda ao gráfico
    plt.legend(handles=legend_elements, loc='upper left', fontsize='small')

    # Definir título do gráfico
    plt.title("Graph with Real-Time Updates")
    plt.pause(0.7)  # Pausar por 0.7 segundos

# Inicialização do gráfico
G = nx.MultiDiGraph()
G.add_nodes_from([1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14])
G.add_edges_from([
    (1, 4), (1, 9), (2, 3), (2, 14), (9, 4), (9, 10), (9, 13), (9, 14), (14, 10), (14, 3), (14, 13), 
    (4, 5), (4, 10), (3, 13), (3, 6), (10, 5), (10, 11), (10, 12), (10, 13), (13, 11), (13, 12), (13, 6),
    (5, 8), (5, 11), (6, 7), (6, 12), (11, 12), (11, 8), (12, 7), (8, 7), (4, 1), (9, 1), (3, 2), (14, 2), 
    (4, 9), (10, 9), (13, 9), (14, 9), (10, 14), (3, 14), (13, 14), (5, 4), (10, 4), (13, 3), (6, 3), (5, 10), 
    (11, 10), (12, 10), (13, 10), (11, 13), (12, 13), (6, 13), (8, 5), (11, 5), (7, 6), (12, 6), (12, 11), 
    (8, 11), (7, 12), (7, 8)
])

edge_colors = {}
edge_update_time = {edge: datetime.min for edge in G.edges()}  # Inicializa o tempo da última atualização com datetime.min

pos = {
    1: (0, 3), 9: (2, 3), 14: (4, 3), 2: (6, 3), 
    4: (0, 2), 10: (2, 2), 13: (4, 2), 3: (6, 2),
    5: (0, 1), 11: (2, 1), 12: (4, 1), 6: (6, 1),
    8: (1, 0), 7: (5, 0)}

# Criar figura para o gráfico
plt.figure(figsize=(12, 8))

# Simulação de leitura contínua de dados
while True:
    query = 'SELECT path, flow_label, src_ip, dst_ip from "flow_stats" WHERE time > now() - 15s'
    result = influx_client.query(query)
    points = result.get_points()
    new_data = list(points)
    # Converter os pontos para uma lista

    if new_data:
        update_graph(G, new_data, edge_update_time)
    
    
    current_time = datetime.now()
    for edge in list(edge_update_time.keys()):
         if (current_time - edge_update_time[edge]) > timedelta(seconds=2):
             edge_colors.pop(edge, None)  # Remove the edge color if not updated in the last 2 seconds

    visualize_graph(G, edge_colors)
    print('\n-= Graph updated at:', datetime.now(), '=-')
    
# Garantir que todos os gráficos sejam exibidos no final
plt.show()
