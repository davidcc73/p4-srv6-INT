import networkx as nx
import matplotlib.pyplot as plt
import itertools

# Function to update the graph
# def update_graph(G, step):
    # Example: Add a new node and an edge every step
    # new_node = len(G.nodes) + 1
    # G.add_node(new_node)
    # G.add_edge(new_node, new_node - 1)

# Function to visualize the graph
def visualize_graph(G, edge_colors, step):
    
    plt.clf()  # Clear the current figure
    nx.draw_networkx_nodes(G, pos, node_size=400, edgecolors= "black")

    for edge in G.edges():
        # if (edge == (1 , 4) or edge == (4, 1)) and step < 3:
        #     edge_colors[edge] = "red"
        # else:
        #     edge_colors[edge] = "black"
        nx.draw_networkx_edges(G, pos, edgelist=[edge], edge_color=edge_colors.get(edge, 'black'), arrows=True, connectionstyle="arc3,rad=0.07")

    nx.draw_networkx_labels(G, pos, font_size=13, font_family="sans-serif")

    plt.title(f"Graph at step {step}")
    plt.pause(0.7)  # Pause for 1 second

# Initialize the graph
G = nx.MultiDiGraph()
G.add_nodes_from([1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14])
G.add_edges_from([
    (1, 4), (1, 9), (2, 3), (2, 14), (9, 4), (9, 10), (9, 13), (9, 14), (14, 10), (14, 3), (14, 13), (4, 5), (4, 10), (3, 13), (3, 6), (10, 5), (10, 11), (10, 12), (10, 13), (13, 11), (13, 12), (13, 6),(5, 8), (5, 11), (6, 7), (6, 12), (11, 12), (11, 8), (12, 7), (8, 7),(4, 1), (9, 1), (3, 2), (14, 2), (4, 9), (10, 9), (13, 9), (14, 9), (10, 14), (3, 14), (13, 14), (5, 4), (10, 4), (13, 3), (6, 3),(5, 10), (11, 10), (12, 10), (13, 10), (11, 13), (12, 13), (6, 13),(8, 5), (11, 5), (7, 6), (12, 6), (12, 11), (8, 11), (7, 12), (7, 8)])

edge_colors = {edge: 'black' for edge in G.edges()}

pos = {
    1: (0, 3), 9: (2, 3), 14: (4, 3), 2: (6, 3), 
    4: (0, 2), 10: (2, 2), 13: (4, 2), 3: (6, 2),
    5: (0, 1), 11: (2, 1), 12: (4, 1), 6: (6, 1),
    8: (1, 0), 7: (5, 0)}

# color_cycle = itertools.cycle(['r', 'g', 'b', 'c', 'm', 'y', 'k'])

# Number of steps to simulate
num_steps = 4

# Create a figure for the plot
plt.figure(figsize=(12, 8))

# Simulate the changes over time
for step in range(num_steps):
    # update_graph(G, step)
    visualize_graph(G, edge_colors, step)

# Ensure all plots are shown at the end
plt.show()
